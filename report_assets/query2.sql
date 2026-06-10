SELECT CC.card_number,
       CC.cust_surname || ' ' || CC.cust_name AS customer,
       CC.phone_number,
       CC.percent
FROM Customer_Card CC
WHERE NOT EXISTS (
    SELECT 1
    FROM Category C
    WHERE NOT EXISTS (
        SELECT 1
        FROM "Check" CH
        JOIN Sale S ON S.check_number = CH.check_number
        JOIN Store_Product SP ON SP.UPC = S.UPC
        JOIN Product P ON P.id_product = SP.id_product
        WHERE CH.card_number = CC.card_number
          AND P.category_number = C.category_number
    )
)
ORDER BY CC.cust_surname, CC.cust_name
LIMIT 5;