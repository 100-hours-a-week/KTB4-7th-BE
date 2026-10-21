-- 기존 solutions 및 saved_solutions 행은 변경하지 않는다.
-- 적용 전 DB 백업과 solutions.original_rank_no 컬럼 부재를 확인한다.
ALTER TABLE solutions
  ADD COLUMN original_rank_no INT NULL AFTER rank_no;

-- 기존 (solution_bundle_id, rank_no) UNIQUE는 보존한다.
-- 보존된 과거 카드에만 원래 표시 순위를 기록한다.
