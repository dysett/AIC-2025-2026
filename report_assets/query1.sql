SELECT C.category_name AS category,
       COUNT(DISTINCT CH.check_number) AS checks_count,
       SUM(S.product_number) AS units_sold,
       ROUND(SUM(S.product_number * S.selling_price), 2) AS sales_sum,
       ROUND(AVG(S.selling_price), 2) AS avg_price
FROM "Check" CH
JOIN Sale S ON S.check_number = CH.check_number
JOIN Store_Product SP ON SP.UPC = S.UPC
JOIN Product P ON P.id_product = SP.id_product
JOIN Category C ON C.category_number = P.category_number
WHERE date(CH.print_date) BETWEEN date(?) AND date(?)
GROUP BY C.category_number, C.category_name
ORDER BY sales_sum DESC, C.category_name
LIMIT 5;