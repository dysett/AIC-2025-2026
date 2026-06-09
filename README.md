# ZLAGODA AIS

Desktop AIS for the grocery mini-supermarket **ZLAGODA**.

## Requirements Covered

- Authentication with login/password.
- Authorization by role: `MANAGER` and `CASHIER`.
- Password encryption/hashing: PBKDF2-HMAC-SHA256 with random salt.
- Report printing through Swing table/receipt printing.
- User interface: Java Swing desktop application.
- No ORM: all database operations are explicit SQL queries through JDBC `PreparedStatement`.

## Tech Stack

- Java 22
- Swing
- SQLite
- SQLite JDBC driver in `lib/sqlite-jdbc-3.53.1.0.jar`

## Run

```powershell
.\run.ps1
```

The database is created automatically in `data/zlagoda.db`.

For a non-GUI verification run:

```powershell
.\smoke-test.ps1
```

Default users:

| Role | Login | Password |
| --- | --- | --- |
| Manager | `manager` | `Manager123!` |
| Cashier | `cashier` | `Cashier123!` |

## Main Features

- Manager can add/edit/delete employees, categories, products, store products, and customer cards.
- Manager can delete checks with stock quantity rollback.
- Manager can find employee phone/address by surname.
- Cashier can search products, maintain customer cards, create checks, and print receipts.
- Cashier can view today's own checks and personal employee information.
- Cashier report access is restricted to own checks where applicable.
- Reports include employees, cashiers, customers, categories, products, store products, checks by period, check details, total sales, cashier sales, and sold product quantity.

## Database Tables

The project follows the relational model from the supermarket requirements:

- `Employee`
- `Customer_Card`
- `Category`
- `Product`
- `Store_Product`
- `"Check"`
- `Sale`
- `App_User` for authentication and authorization.

All business data access is implemented with clean SQL, not ORM.
