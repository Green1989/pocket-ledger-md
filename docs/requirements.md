# Simple Android Markdown Ledger - Requirements

## Goal
Build a simple Android single-user bookkeeping app. All records are saved to local Markdown files.

## Must Have
1. Add entry
- datetime (default now)
- amount (required)
- type (income/expense)
- category
- note (optional)

2. Save to Markdown (local only)
- one file per month: `YYYY-MM.md`
- one day section: `## YYYY-MM-DD`
- one line per record

3. View current month
- list records in reverse time order
- monthly totals: income, expense, balance

## Non-Goals
- No backend/frontend separation
- No SQL/Redis
- No Docker
- No OCR/AI in this phase

## Markdown format

```md
# 2026-02 Ledger

## 2026-02-26
- 09:30 | expense | 25.50 | 餐饮 | 早餐
- 19:10 | expense | 68.00 | 交通 | 打车
- 21:00 | income | 500.00 | 其他 | 兼职
```

- Line format:
`- HH:mm | type | amount | category | note`
- `type`: `income` or `expense`
- amount uses 2 decimal places
