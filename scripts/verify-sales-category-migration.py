#!/usr/bin/env python3
"""Verify the migration against an isolated, disposable MySQL 8.4 container."""
import csv
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
NAME = "memme-category-test-" + uuid.uuid4().hex[:10]


def sql(statement, check=True):
    result = subprocess.run(["docker", "exec", "-i", NAME, "mysql", "--default-character-set=utf8mb4",
                           "-uroot", "--batch", "--skip-column-names", "category_test"],
                          input=statement, text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError(result.stderr)
    return result


def quote(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def verify():
    subprocess.run(["python3", str(ROOT / "scripts/generate-sales-category-migration.py"), "--check"], check=True)
    subprocess.run(["docker", "run", "--detach", "--rm", "--name", NAME,
                    "--network", "none", "--env", "MYSQL_ALLOW_EMPTY_PASSWORD=yes",
                    "--env", "MYSQL_DATABASE=category_test", "mysql:8.4"],
                   check=True, capture_output=True, text=True)
    try:
        for _ in range(60):
            if sql("SELECT 1;", check=False).returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("Test MySQL did not become ready")

        sql("""CREATE TABLE sales_order_items (
          id BIGINT PRIMARY KEY, standard_category VARCHAR(30) NOT NULL,
          category_raw VARCHAR(100), menu_name VARCHAR(100) NOT NULL,
          net_amount BIGINT NOT NULL, quantity INT NOT NULL
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;""")
        with (ROOT / "src/test/resources/sales/menu-category-cases.csv").open() as source:
            cases = list(csv.DictReader(source))
        expected = {}
        for index, case in enumerate(cases, 1):
            sql(f"INSERT INTO sales_order_items VALUES ({index}, 'UNMAPPED', "
                f"{quote(case['rawCategory'])}, {quote(case['menuName'])}, {index * 100}, 1);")
            expected[index] = case["expected"]

        # Canonical choices survive; legacy beverage categories must never become TEA.
        legacy = {"COFFEE": "COFFEE", "TEA": "TEA", "BEVERAGE": "BEVERAGE", "CAKE": "CAKE",
                  "BAKERY": "BAKERY", "OTHER": "OTHER", "NON_COFFEE": "BEVERAGE", "ADE": "BEVERAGE",
                  "SMOOTHIE": "BEVERAGE", "BOTTLED_DRINK": "BEVERAGE", "BEER": "BEVERAGE",
                  "FOOD": "OTHER", "SEASONAL": "OTHER", "UNMAPPED": "OTHER"}
        for index, (old, new) in enumerate(legacy.items(), len(cases) + 1):
            sql(f"INSERT INTO sales_order_items VALUES ({index}, {quote(old)}, '알수없음', '상품', -100, -1);")
            expected[index] = new
        snapshot = sql("SELECT id, category_raw, menu_name, net_amount, quantity FROM sales_order_items ORDER BY id;").stdout
        sql((ROOT / "docker/mysql/migrations/20261027-unify-sales-menu-categories.sql").read_text())
        assert sql("SELECT id, category_raw, menu_name, net_amount, quantity FROM sales_order_items ORDER BY id;").stdout == snapshot
        results = sql("SELECT id, standard_category FROM sales_order_items ORDER BY id;").stdout.splitlines()
        for row in results:
            index, category = row.split("\t")
            assert category == expected[int(index)], (index, category, expected[int(index)])
        assert len(results) == len(expected)
        rejected = sql("UPDATE sales_order_items SET standard_category = 'NON_COFFEE' WHERE id = 1;", check=False)
        assert rejected.returncode != 0 and "ck_sales_item_standard_category" in rejected.stderr, rejected

        # Validate the exact new-install CHECK as well, without changing the full schema's FKs.
        init = (ROOT / "docker/mysql/init/02-sales-core.sql").read_text()
        constraint = init.split("CONSTRAINT ck_sales_item_standard_category CHECK", 1)[1].split(",\n", 1)[0]
        sql(f"CREATE TABLE new_install (standard_category VARCHAR(30) NOT NULL, "
            f"CONSTRAINT ck_new_install CHECK {constraint});")
        assert sql("INSERT INTO new_install VALUES ('UNMAPPED');", check=False).returncode != 0
        sql("INSERT INTO new_install VALUES ('COFFEE'), ('TEA'), ('BEVERAGE'), ('CAKE'), ('BAKERY'), ('OTHER');")
        print(f"MySQL 8.4: {len(expected)} rows matched Java classification cases and legacy mapping; "
              "amounts, quantities and raw labels preserved; legacy writes rejected.")
    finally:
        subprocess.run(["docker", "rm", "--force", NAME], check=False, capture_output=True)


if __name__ == "__main__":
    verify()
