const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const baseName = '2_Пацеля';
const outPath = path.join(root, `${baseName}.docx`);
const assets = path.join(root, 'report_assets');

const query1Interface = path.join(assets, 'query1_interface.png');
const query1Result = path.join(assets, 'query1_result.png');
const query2Interface = path.join(assets, 'query2_interface.png');
const query2Result = path.join(assets, 'query2_result.png');

const query1Sql = `SELECT C.category_name AS category,
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
LIMIT 5;`;

const query2Sql = `SELECT CC.card_number,
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
LIMIT 5;`;

const codeExample = `// Кнопка "Сформувати" запускає виконання звіту з графічного інтерфейсу.
JButton run = new JButton("Сформувати");
run.addActionListener(e -> runReport());

private void runReport() {
    try {
        String r = (String) report.getSelectedItem();

        // Якщо користувач вибрав потрібний звіт, запускається метод із SQL-запитом.
        switch (r) {
            case "Звіт 3Т: продажі категорій за період" ->
                    table.setModel(categorySalesForCoursework());
        }
    } catch (SQLException | NumberFormatException ex) {
        // Помилка SQL або параметрів показується користувачу у діалоговому вікні.
        JOptionPane.showMessageDialog(this, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);
    }
}

private DefaultTableModel categorySalesForCoursework() throws SQLException {
    // Параметри беруться з полів "Від" і "До", які користувач заповнює в інтерфейсі.
    // Запит не складається конкатенацією рядків: значення передаються через PreparedStatement.
    return Db.tableModel("""
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
            LIMIT 5
            """, from.getText().trim(), to.getText().trim());
}

public static DefaultTableModel tableModel(String sql, Object... params) throws SQLException {
    // Встановлюється з'єднання з базою SQLite.
    try (Connection con = connect();
         // SQL передається серверу бази як PreparedStatement.
         PreparedStatement ps = con.prepareStatement(sql)) {

        // Параметри дати підставляються у знаки питання без ризику SQL injection.
        bind(ps, params);

        // Сервер виконує SELECT і повертає ResultSet.
        try (ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            int columns = md.getColumnCount();
            String[] names = new String[columns];
            for (int i = 0; i < columns; i++) {
                names[i] = md.getColumnLabel(i + 1);
            }

            // Дані перетворюються на модель таблиці Swing, яку бачить користувач.
            DefaultTableModel model = new DefaultTableModel(names, 0);
            while (rs.next()) {
                Object[] row = new Object[columns];
                for (int i = 0; i < columns; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                model.addRow(row);
            }
            return model;
        }
    }
}`;

fs.writeFileSync(path.join(assets, 'query1.sql'), query1Sql, 'utf8');
fs.writeFileSync(path.join(assets, 'query2.sql'), query2Sql, 'utf8');
fs.writeFileSync(path.join(assets, 'code_example.java.txt'), codeExample, 'utf8');

function crc32(buf) {
  if (!crc32.table) {
    crc32.table = new Uint32Array(256);
    for (let i = 0; i < 256; i++) {
      let c = i;
      for (let j = 0; j < 8; j++) {
        c = (c & 1) ? (0xedb88320 ^ (c >>> 1)) : (c >>> 1);
      }
      crc32.table[i] = c >>> 0;
    }
  }
  let crc = 0xffffffff;
  for (const byte of buf) {
    crc = crc32.table[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function dosDateTime(date = new Date()) {
  const year = Math.max(1980, date.getFullYear());
  return {
    dosTime: (date.getHours() << 11) | (date.getMinutes() << 5) | Math.floor(date.getSeconds() / 2),
    dosDate: ((year - 1980) << 9) | ((date.getMonth() + 1) << 5) | date.getDate(),
  };
}

function makeZip(entries) {
  const chunks = [];
  const central = [];
  let offset = 0;
  const { dosTime, dosDate } = dosDateTime();

  for (const entry of entries) {
    const name = entry.name.replace(/\\/g, '/');
    const nameBuf = Buffer.from(name, 'utf8');
    const data = Buffer.isBuffer(entry.data) ? entry.data : Buffer.from(entry.data, 'utf8');
    const crc = crc32(data);

    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(0, 8);
    local.writeUInt16LE(dosTime, 10);
    local.writeUInt16LE(dosDate, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(data.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    local.writeUInt16LE(0, 28);
    chunks.push(local, nameBuf, data);

    const centralHeader = Buffer.alloc(46);
    centralHeader.writeUInt32LE(0x02014b50, 0);
    centralHeader.writeUInt16LE(20, 4);
    centralHeader.writeUInt16LE(20, 6);
    centralHeader.writeUInt16LE(0x0800, 8);
    centralHeader.writeUInt16LE(0, 10);
    centralHeader.writeUInt16LE(dosTime, 12);
    centralHeader.writeUInt16LE(dosDate, 14);
    centralHeader.writeUInt32LE(crc, 16);
    centralHeader.writeUInt32LE(data.length, 20);
    centralHeader.writeUInt32LE(data.length, 24);
    centralHeader.writeUInt16LE(nameBuf.length, 28);
    centralHeader.writeUInt16LE(0, 30);
    centralHeader.writeUInt16LE(0, 32);
    centralHeader.writeUInt16LE(0, 34);
    centralHeader.writeUInt16LE(0, 36);
    centralHeader.writeUInt32LE(0, 38);
    centralHeader.writeUInt32LE(offset, 42);
    central.push(centralHeader, nameBuf);
    offset += local.length + nameBuf.length + data.length;
  }

  const centralSize = central.reduce((sum, part) => sum + part.length, 0);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(0, 4);
  end.writeUInt16LE(0, 6);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(centralSize, 12);
  end.writeUInt32LE(offset, 16);
  end.writeUInt16LE(0, 20);
  return Buffer.concat([...chunks, ...central, end]);
}

function esc(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function pngSize(filePath) {
  const data = fs.readFileSync(filePath);
  if (data.toString('ascii', 1, 4) !== 'PNG') {
    throw new Error(`Not a PNG: ${filePath}`);
  }
  return { width: data.readUInt32BE(16), height: data.readUInt32BE(20) };
}

function runProps({ bold = false, size = 24, font = 'Calibri' } = {}) {
  return `<w:rPr><w:rFonts w:ascii="${font}" w:hAnsi="${font}" w:cs="${font}"/>${bold ? '<w:b/>' : ''}<w:sz w:val="${size}"/><w:szCs w:val="${size}"/></w:rPr>`;
}

function paragraph(text = '', options = {}) {
  const spacing = options.after == null ? 120 : options.after;
  const align = options.align ? `<w:jc w:val="${options.align}"/>` : '';
  return `<w:p><w:pPr><w:spacing w:after="${spacing}"/>${align}</w:pPr><w:r>${runProps(options)}<w:t xml:space="preserve">${esc(text)}</w:t></w:r></w:p>`;
}

function codeBlock(text) {
  return text.split(/\r?\n/).map(line =>
    `<w:p><w:pPr><w:spacing w:after="0" w:line="220" w:lineRule="auto"/></w:pPr><w:r>${runProps({ size: 17, font: 'Courier New' })}<w:t xml:space="preserve">${esc(line)}</w:t></w:r></w:p>`
  ).join('');
}

let drawingId = 1;
function imageParagraph(rId, name, filePath, widthInches) {
  const size = pngSize(filePath);
  const cx = Math.round(widthInches * 914400);
  const cy = Math.round(cx * size.height / size.width);
  const id = drawingId++;
  return `<w:p><w:pPr><w:spacing w:after="180"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="${cx}" cy="${cy}"/><wp:effectExtent l="0" t="0" r="0" b="0"/><wp:docPr id="${id}" name="${esc(name)}"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect="1"/></wp:cNvGraphicFramePr><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="${id}" name="${esc(name)}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="${rId}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="${cx}" cy="${cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>`;
}

function pageBreak() {
  return '<w:p><w:r><w:br w:type="page"/></w:r></w:p>';
}

const body = [
  paragraph('АІС Звіт (3 триместр) (2025-2026)', { bold: true, size: 36, after: 160, align: 'center' }),
  paragraph('Розробка АІС для продуктового міні-супермаркету «ZLAGODA»', { size: 26, after: 100, align: 'center' }),
  paragraph('Група 2, Пацеля', { size: 26, after: 320, align: 'center' }),

  paragraph('Запит 1. Багатотабличний параметричний запит із групуванням', { bold: true, size: 30, after: 160 }),
  paragraph('Умова запиту:', { bold: true }),
  paragraph('Вивести до 5 категорій товарів із найбільшою сумою продажів за вибраний період. Для кожної категорії показати кількість чеків, кількість проданих одиниць, суму продажів і середню ціну продажу. Період задається параметрами «Від» і «До» в інтерфейсі користувача. Запит використовує таблиці Check, Sale, Store_Product, Product і Category та групує дані за категорією.', { after: 160 }),
  paragraph('SQL-код запиту:', { bold: true }),
  codeBlock(query1Sql),
  paragraph('Скріншот з інтерфейсу користувача:', { bold: true, after: 80 }),
  imageParagraph('rIdQ1Interface', 'query1_interface.png', query1Interface, 6.8),
  paragraph('Результат виконання запиту на графічний інтерфейс:', { bold: true, after: 80 }),
  imageParagraph('rIdQ1Result', 'query1_result.png', query1Result, 6.8),
  pageBreak(),

  paragraph('Запит 2. Багатотабличний запит із подвійним запереченням', { bold: true, size: 30, after: 160 }),
  paragraph('Умова запиту:', { bold: true }),
  paragraph('Знайти постійних клієнтів, які купили хоча б один товар з кожної наявної категорії товарів. Подвійне заперечення реалізоване через NOT EXISTS ... NOT EXISTS: не існує категорії, для якої не існує покупки цього клієнта. Запит використовує таблиці Customer_Card, Category, Check, Sale, Store_Product і Product.', { after: 160 }),
  paragraph('SQL-код запиту:', { bold: true }),
  codeBlock(query2Sql),
  paragraph('Скріншот з інтерфейсу користувача:', { bold: true, after: 80 }),
  imageParagraph('rIdQ2Interface', 'query2_interface.png', query2Interface, 6.8),
  paragraph('Результат виконання запиту на графічний інтерфейс:', { bold: true, after: 80 }),
  imageParagraph('rIdQ2Result', 'query2_result.png', query2Result, 6.8),
  pageBreak(),

  paragraph('Код прикладної програми для виконання запиту 1', { bold: true, size: 30, after: 160 }),
  paragraph('Наведений фрагмент показує повну послідовність дій: натискання кнопки, вибір звіту, формування SQL-запиту, передавання параметрів серверу бази даних, отримання ResultSet і виведення відповіді у JTable.', { after: 160 }),
  codeBlock(codeExample),
].join('');

const documentXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
  <w:body>
    ${body}
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720" w:header="360" w:footer="360" w:gutter="0"/>
      <w:cols w:space="708"/>
      <w:docGrid w:linePitch="360"/>
    </w:sectPr>
  </w:body>
</w:document>`;

const contentTypesXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="png" ContentType="image/png"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>`;

const packageRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>`;

const documentRelsXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdQ1Interface" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/query1_interface.png"/>
  <Relationship Id="rIdQ1Result" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/query1_result.png"/>
  <Relationship Id="rIdQ2Interface" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/query2_interface.png"/>
  <Relationship Id="rIdQ2Result" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/query2_result.png"/>
</Relationships>`;

const created = new Date().toISOString();
const coreXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>${esc(baseName)}</dc:title>
  <dc:creator>Codex</dc:creator>
  <cp:lastModifiedBy>Codex</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">${created}</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">${created}</dcterms:modified>
</cp:coreProperties>`;

const appXml = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
  <Application>Microsoft Word</Application>
  <DocSecurity>0</DocSecurity>
  <ScaleCrop>false</ScaleCrop>
</Properties>`;

const docxEntries = [
  { name: '[Content_Types].xml', data: contentTypesXml },
  { name: '_rels/.rels', data: packageRelsXml },
  { name: 'docProps/core.xml', data: coreXml },
  { name: 'docProps/app.xml', data: appXml },
  { name: 'word/document.xml', data: documentXml },
  { name: 'word/_rels/document.xml.rels', data: documentRelsXml },
  { name: 'word/media/query1_interface.png', data: fs.readFileSync(query1Interface) },
  { name: 'word/media/query1_result.png', data: fs.readFileSync(query1Result) },
  { name: 'word/media/query2_interface.png', data: fs.readFileSync(query2Interface) },
  { name: 'word/media/query2_result.png', data: fs.readFileSync(query2Result) },
];

fs.writeFileSync(outPath, makeZip(docxEntries));
console.log(outPath);
