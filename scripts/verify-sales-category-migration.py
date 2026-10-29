#!/usr/bin/env python3
"""Verify VARCHAR and legacy native ENUM migrations in disposable MySQL 8.4."""
import csv
from pathlib import Path
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parents[1]
NAME = "memme-category-test-" + uuid.uuid4().hex[:10]
MIGRATIONS = ROOT / "docker/mysql/migrations"
# The twelve values accepted by the old sales enum (BEVERAGE/OTHER are absent).
LEGACY_ENUM = ("ADE", "BAKERY", "BEER", "BOTTLED_DRINK", "CAKE", "COFFEE",
               "FOOD", "NON_COFFEE", "SEASONAL", "SMOOTHIE", "TEA", "UNMAPPED")
PRESERVED_COLUMNS = "id, category_raw, menu_name, net_amount, quantity"


def sql(statement, check=True):
    result = subprocess.run(["docker", "exec", "-i", NAME, "mysql", "--default-character-set=utf8mb4",
                           "-uroot", "--batch", "--skip-column-names", "category_test"],
                          input=statement, text=True, capture_output=True)
    if check and result.returncode:
        raise RuntimeError(result.stderr)
    return result


def quote(value):
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def snapshot(columns="*"):
    return sql(f"SELECT {columns} FROM sales_order_items ORDER BY id;").stdout


def column_definition():
    return sql("""SELECT COLUMN_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_order_items'
        AND COLUMN_NAME = 'standard_category';""").stdout.strip()


def verify_existing(column_type, native_enum=False):
    sql(f"""CREATE TABLE sales_order_items (
      id BIGINT PRIMARY KEY, standard_category {column_type} NOT NULL,
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
    if native_enum:
        legacy = {old: new for old, new in legacy.items() if old in LEGACY_ENUM}
    for index, (old, new) in enumerate(legacy.items(), len(cases) + 1):
        sql(f"INSERT INTO sales_order_items VALUES ({index}, {quote(old)}, '알수없음', '상품', -100, -1);")
        expected[index] = new

    original = snapshot()
    original_definition = column_definition()
    preserved = snapshot(PRESERVED_COLUMNS)
    migration = (MIGRATIONS / "20261027-unify-sales-menu-categories.sql").read_text()
    if native_enum:
        for category in ("BEVERAGE", "OTHER"):
            rejected = sql(f"UPDATE sales_order_items SET standard_category = {quote(category)} WHERE id = 1;",
                           check=False)
            assert rejected.returncode != 0 and "ERROR 1265" in rejected.stderr, rejected
        # Exercise the actual unmodified backfill, not just an equivalent UPDATE.
        rejected = sql(migration, check=False)
        assert rejected.returncode != 0 and "ERROR 1265" in rejected.stderr, rejected
        assert snapshot() == original, "Failed ENUM migration changed data"

    # A local backup table models the stopped-writer backup/restore procedure.
    sql("CREATE TABLE sales_category_backup LIKE sales_order_items; "
        "INSERT INTO sales_category_backup SELECT * FROM sales_order_items;")
    sql((MIGRATIONS / "20261029-prepare-sales-category-varchar.sql").read_text())
    assert column_definition() == "varchar(30)\tNO", column_definition()
    assert snapshot() == original, "Type conversion changed existing values"
    sql(migration)
    assert snapshot(PRESERVED_COLUMNS) == preserved, "Backfill changed source data"
    results = sql("SELECT id, standard_category FROM sales_order_items ORDER BY id;").stdout.splitlines()
    for row in results:
        index, category = row.split("\t")
        assert category == expected[int(index)], (index, category, expected[int(index)])
    assert len(results) == len(expected)
    rejected = sql("UPDATE sales_order_items SET standard_category = 'NON_COFFEE' WHERE id = 1;", check=False)
    assert rejected.returncode != 0 and "ck_sales_item_standard_category" in rejected.stderr, rejected
    assert sql("UPDATE sales_order_items SET standard_category = NULL WHERE id = 1;", check=False).returncode != 0

    # Restore values BEFORE narrowing the column back to the original native ENUM.
    sql("ALTER TABLE sales_order_items DROP CHECK ck_sales_item_standard_category;")
    sql("UPDATE sales_order_items AS item JOIN sales_category_backup AS backup ON backup.id = item.id "
        "SET item.standard_category = backup.standard_category;")
    assert snapshot() == original, "Backup did not restore the original categories"
    original_type = original_definition.split("\t")[0]
    sql(f"ALTER TABLE sales_order_items MODIFY COLUMN standard_category {original_type} NOT NULL;")
    assert column_definition() == original_definition, "Original column definition was not restored"
    assert snapshot() == original, "Restoring column type changed data"
    sql("DROP TABLE sales_category_backup; DROP TABLE sales_order_items;")
    print(f"MySQL 8.4 {'native ENUM' if native_enum else 'VARCHAR'}: {len(expected)} rows classified; "
          "type conversion, source/amount/quantity preservation, CHECK and backup restore passed.")


def verify_new_install():
    # Validate the exact new-install CHECK without changing the full schema's FKs.
    init = (ROOT / "docker/mysql/init/02-sales-core.sql").read_text()
    constraint = init.split("CONSTRAINT ck_sales_item_standard_category CHECK", 1)[1].split(",\n", 1)[0]
    sql(f"CREATE TABLE new_install (standard_category VARCHAR(30) NOT NULL, "
        f"CONSTRAINT ck_new_install CHECK {constraint});")
    assert sql("INSERT INTO new_install VALUES ('UNMAPPED');", check=False).returncode != 0
    sql("INSERT INTO new_install VALUES ('COFFEE'), ('TEA'), ('BEVERAGE'), ('CAKE'), ('BAKERY'), ('OTHER');")
    print("MySQL 8.4 new install: six canonical categories accepted; legacy value rejected.")


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
        verify_existing("VARCHAR(30)")
        verify_existing("ENUM(" + ",".join(quote(value) for value in LEGACY_ENUM) + ")", native_enum=True)
        verify_new_install()
    finally:
        subprocess.run(["docker", "rm", "--force", NAME], check=False, capture_output=True)


if __name__ == "__main__":
    verify()
