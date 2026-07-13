package com.example.backend.service;

import com.example.backend.dto.financials.AnnualWithdrawalSnapshotRequest;
import com.example.backend.dto.financials.AssetAccountSnapshotRequest;
import com.example.backend.dto.financials.AssetCategorySnapshotRequest;
import com.example.backend.dto.financials.DebtAccountSnapshotRequest;
import com.example.backend.dto.financials.ExpenseBillSnapshotRequest;
import com.example.backend.dto.financials.ExpenseSnapshotRequest;
import com.example.backend.dto.financials.ImportantDateSnapshotRequest;
import com.example.backend.dto.financials.IncomeEventSnapshotRequest;
import com.example.backend.dto.financials.IncomeSummaryItemSnapshotRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class FinancialSnapshotTabularCodec {

  private static final String OPEN_XML_MAIN_NS =
      "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
  private static final List<String> COLUMNS =
      List.of(
          "recordType",
          "version",
          "id",
          "payPeriodStart",
          "payPeriodEnd",
          "bill",
          "dueDay",
          "month",
          "day",
          "amount",
          "account",
          "paid",
          "categoryKey",
          "categoryLabel",
          "company",
          "category",
          "interval",
          "date",
          "label",
          "event",
          "type",
          "checkNumber");
  private static final Map<String, Integer> COLUMN_INDEX = columnIndex();
  private static final Set<String> NUMERIC_COLUMNS =
      Set.of("version", "id", "dueDay", "month", "day", "amount", "checkNumber");
  private static final Set<String> BOOLEAN_COLUMNS = Set.of("paid");

  byte[] toCsv(ExpenseSnapshotRequest snapshot) {
    StringBuilder csv = new StringBuilder();
    for (List<String> row : toRows(snapshot)) {
      for (int column = 0; column < row.size(); column++) {
        if (column > 0) {
          csv.append(',');
        }
        csv.append(escapeCsv(row.get(column)));
      }
      csv.append('\n');
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  ExpenseSnapshotRequest fromCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      throw new IllegalArgumentException("CSV import is empty");
    }
    return fromRows(parseCsv(csv));
  }

  byte[] toXlsx(ExpenseSnapshotRequest snapshot) {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
      addZipEntry(zip, "[Content_Types].xml", contentTypesXml());
      addZipEntry(zip, "_rels/.rels", packageRelationshipsXml());
      addZipEntry(zip, "xl/workbook.xml", workbookXml());
      addZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelationshipsXml());
      addZipEntry(zip, "xl/worksheets/sheet1.xml", worksheetXml(toRows(snapshot)));
      zip.finish();
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create XLSX export", exception);
    }
  }

  ExpenseSnapshotRequest fromXlsx(byte[] workbook) {
    if (workbook == null || workbook.length == 0) {
      throw new IllegalArgumentException("XLSX import is empty");
    }

    Map<String, byte[]> entries = readZipEntries(workbook);
    byte[] worksheet = firstWorksheet(entries);
    if (worksheet == null) {
      throw new IllegalArgumentException("XLSX import is missing a worksheet");
    }

    List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
    return fromRows(parseWorksheetRows(worksheet, sharedStrings));
  }

  private List<List<String>> toRows(ExpenseSnapshotRequest snapshot) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(COLUMNS);

    List<String> metadata = emptyRow("snapshot");
    set(metadata, "version", number(snapshot.version()));
    set(metadata, "payPeriodStart", date(snapshot.payPeriodStart()));
    set(metadata, "payPeriodEnd", date(snapshot.payPeriodEnd()));
    rows.add(metadata);

    nullSafe(snapshot.bills())
        .forEach(
            (bill) -> {
              List<String> row = emptyRow("bill");
              set(row, "id", number(bill.id()));
              set(row, "bill", bill.bill());
              set(row, "dueDay", number(bill.dueDay()));
              set(row, "amount", money(bill.amount()));
              set(row, "account", bill.account());
              set(row, "paid", Boolean.toString(bill.paid()));
              rows.add(row);
            });

    nullSafe(snapshot.annualWithdrawals())
        .forEach(
            (withdrawal) -> {
              List<String> row = emptyRow("annualWithdrawal");
              set(row, "id", number(withdrawal.id()));
              set(row, "bill", withdrawal.bill());
              set(row, "month", number(withdrawal.month()));
              set(row, "day", number(withdrawal.day()));
              set(row, "amount", money(withdrawal.amount()));
              set(row, "account", withdrawal.account());
              set(row, "paid", Boolean.toString(withdrawal.paid()));
              rows.add(row);
            });

    nullSafe(snapshot.assetCategories())
        .forEach(
            (category) ->
                nullSafe(category.accounts())
                    .forEach(
                        (account) -> {
                          List<String> row = emptyRow("assetAccount");
                          set(row, "id", number(account.id()));
                          set(row, "categoryKey", category.key());
                          set(row, "categoryLabel", category.label());
                          set(row, "account", account.account());
                          set(row, "company", account.company());
                          set(row, "amount", money(account.amount()));
                          rows.add(row);
                        }));

    nullSafe(snapshot.debtAccounts())
        .forEach(
            (account) -> {
              List<String> row = emptyRow("debtAccount");
              set(row, "id", number(account.id()));
              set(row, "account", account.account());
              set(row, "company", account.company());
              set(row, "amount", money(account.amount()));
              rows.add(row);
            });

    nullSafe(snapshot.incomeSummaryItems())
        .forEach(
            (item) -> {
              List<String> row = emptyRow("incomeSummaryItem");
              set(row, "id", number(item.id()));
              set(row, "category", item.category());
              set(row, "interval", item.interval());
              set(row, "amount", money(item.amount()));
              rows.add(row);
            });

    nullSafe(snapshot.incomeEvents())
        .forEach(
            (event) -> {
              List<String> row = emptyRow("incomeEvent");
              set(row, "id", number(event.id()));
              set(row, "date", date(event.date()));
              set(row, "label", event.label());
              set(row, "type", event.type());
              set(row, "checkNumber", number(event.checkNumber()));
              rows.add(row);
            });

    nullSafe(snapshot.importantDates())
        .forEach(
            (importantDate) -> {
              List<String> row = emptyRow("importantDate");
              set(row, "id", number(importantDate.id()));
              set(row, "date", date(importantDate.date()));
              set(row, "event", importantDate.event());
              set(row, "type", importantDate.type());
              rows.add(row);
            });

    return rows;
  }

  private ExpenseSnapshotRequest fromRows(List<TabularRow> importedRows) {
    List<TabularRow> rows =
        importedRows.stream().filter((row) -> !isBlank(row.values())).map(this::normalize).toList();
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("Import is missing a header row");
    }

    validateHeader(rows.getFirst());

    ParsedSnapshot parsed = new ParsedSnapshot();
    for (int index = 1; index < rows.size(); index++) {
      TabularRow row = rows.get(index);
      String recordType = requiredText(row, "recordType");
      switch (recordType.toLowerCase()) {
        case "snapshot" -> parsed.readMetadata(row);
        case "bill" ->
            parsed.bills.add(
                new ExpenseBillSnapshotRequest(
                    optionalLong(row, "id"),
                    text(row, "bill"),
                    requiredInt(row, "dueDay"),
                    requiredDecimal(row, "amount"),
                    text(row, "account"),
                    requiredBoolean(row, "paid")));
        case "annualwithdrawal" ->
            parsed.annualWithdrawals.add(
                new AnnualWithdrawalSnapshotRequest(
                    optionalLong(row, "id"),
                    text(row, "bill"),
                    requiredInt(row, "month"),
                    requiredInt(row, "day"),
                    requiredDecimal(row, "amount"),
                    text(row, "account"),
                    requiredBoolean(row, "paid")));
        case "assetaccount" -> parsed.addAssetAccount(row);
        case "debtaccount" ->
            parsed.debtAccounts.add(
                new DebtAccountSnapshotRequest(
                    optionalLong(row, "id"),
                    text(row, "account"),
                    text(row, "company"),
                    requiredDecimal(row, "amount")));
        case "incomesummaryitem" ->
            parsed.incomeSummaryItems.add(
                new IncomeSummaryItemSnapshotRequest(
                    optionalLong(row, "id"),
                    text(row, "category"),
                    text(row, "interval"),
                    requiredDecimal(row, "amount")));
        case "incomeevent" ->
            parsed.incomeEvents.add(
                new IncomeEventSnapshotRequest(
                    optionalLong(row, "id"),
                    requiredDate(row, "date"),
                    text(row, "label"),
                    text(row, "type"),
                    optionalInt(row, "checkNumber")));
        case "importantdate" ->
            parsed.importantDates.add(
                new ImportantDateSnapshotRequest(
                    optionalLong(row, "id"),
                    requiredDate(row, "date"),
                    text(row, "event"),
                    text(row, "type")));
        default -> throw rowError(row, "Unknown recordType");
      }
    }

    return parsed.toSnapshotRequest();
  }

  private TabularRow normalize(TabularRow row) {
    List<String> values = row.values();
    if (values.size() == COLUMNS.size()) {
      return row;
    }

    if (values.size() > COLUMNS.size()) {
      List<String> extraValues = values.subList(COLUMNS.size(), values.size());
      if (extraValues.stream().anyMatch((value) -> !value.isBlank())) {
        throw rowError(row, "Unexpected extra columns");
      }
      values = values.subList(0, COLUMNS.size());
    }

    List<String> normalized = new ArrayList<>(values);
    while (normalized.size() < COLUMNS.size()) {
      normalized.add("");
    }
    return new TabularRow(row.rowNumber(), normalized);
  }

  private void validateHeader(TabularRow headerRow) {
    List<String> header = new ArrayList<>(headerRow.values());
    if (!header.isEmpty() && header.getFirst().startsWith("\uFEFF")) {
      header.set(0, header.getFirst().substring(1));
    }

    if (!COLUMNS.equals(header)) {
      throw new IllegalArgumentException(
          "Import header does not match the financial snapshot tabular format");
    }
  }

  private List<TabularRow> parseCsv(String csv) {
    List<TabularRow> rows = new ArrayList<>();
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    int rowNumber = 1;

    for (int index = 0; index < csv.length(); index++) {
      char character = csv.charAt(index);
      if (quoted) {
        if (character == '"') {
          if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
            field.append('"');
            index++;
          } else {
            quoted = false;
          }
        } else {
          field.append(character);
        }
      } else if (character == '"' && field.isEmpty()) {
        quoted = true;
      } else if (character == ',') {
        fields.add(field.toString());
        field.setLength(0);
      } else if (character == '\r' || character == '\n') {
        fields.add(field.toString());
        addCsvRow(rows, rowNumber, fields);
        fields = new ArrayList<>();
        field.setLength(0);
        if (character == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') {
          index++;
        }
        rowNumber++;
      } else {
        field.append(character);
      }
    }

    if (quoted) {
      throw new IllegalArgumentException("CSV import has an unclosed quoted field");
    }

    fields.add(field.toString());
    addCsvRow(rows, rowNumber, fields);
    return rows;
  }

  private void addCsvRow(List<TabularRow> rows, int rowNumber, List<String> fields) {
    if (fields.stream().anyMatch((field) -> !field.isBlank())) {
      rows.add(new TabularRow(rowNumber, List.copyOf(fields)));
    }
  }

  private List<TabularRow> parseWorksheetRows(byte[] worksheet, List<String> sharedStrings) {
    Document document = parseXml(worksheet, "Invalid XLSX worksheet XML");
    NodeList rowNodes = document.getElementsByTagNameNS(OPEN_XML_MAIN_NS, "row");
    List<TabularRow> rows = new ArrayList<>();

    for (int rowIndex = 0; rowIndex < rowNodes.getLength(); rowIndex++) {
      Element rowElement = (Element) rowNodes.item(rowIndex);
      int rowNumber = intAttribute(rowElement, "r", rowIndex + 1);
      List<String> values = new ArrayList<>();
      NodeList cellNodes = rowElement.getElementsByTagNameNS(OPEN_XML_MAIN_NS, "c");

      for (int cellIndex = 0; cellIndex < cellNodes.getLength(); cellIndex++) {
        Element cellElement = (Element) cellNodes.item(cellIndex);
        int columnIndex = columnIndex(cellElement.getAttribute("r"), cellIndex);
        while (values.size() <= columnIndex) {
          values.add("");
        }
        values.set(columnIndex, cellText(cellElement, sharedStrings));
      }

      rows.add(new TabularRow(rowNumber, values));
    }

    return rows;
  }

  private List<String> parseSharedStrings(byte[] sharedStringsXml) {
    if (sharedStringsXml == null) {
      return List.of();
    }

    Document document = parseXml(sharedStringsXml, "Invalid XLSX shared strings XML");
    NodeList stringItems = document.getElementsByTagNameNS(OPEN_XML_MAIN_NS, "si");
    List<String> values = new ArrayList<>();

    for (int index = 0; index < stringItems.getLength(); index++) {
      Element stringItem = (Element) stringItems.item(index);
      NodeList textNodes = stringItem.getElementsByTagNameNS(OPEN_XML_MAIN_NS, "t");
      StringBuilder value = new StringBuilder();
      for (int textIndex = 0; textIndex < textNodes.getLength(); textIndex++) {
        value.append(textNodes.item(textIndex).getTextContent());
      }
      values.add(value.toString());
    }

    return values;
  }

  private Document parseXml(byte[] xml, String errorMessage) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    } catch (IOException | ParserConfigurationException | SAXException exception) {
      throw new IllegalArgumentException(errorMessage, exception);
    }
  }

  private Map<String, byte[]> readZipEntries(byte[] workbook) {
    Map<String, byte[]> entries = new HashMap<>();
    try (ZipInputStream zip =
        new ZipInputStream(new ByteArrayInputStream(workbook), StandardCharsets.UTF_8)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          entries.put(entry.getName(), zip.readAllBytes());
        }
      }
      return entries;
    } catch (IOException exception) {
      throw new IllegalArgumentException("Invalid XLSX workbook", exception);
    }
  }

  private byte[] firstWorksheet(Map<String, byte[]> entries) {
    byte[] sheet1 = entries.get("xl/worksheets/sheet1.xml");
    if (sheet1 != null) {
      return sheet1;
    }

    return entries.entrySet().stream()
        .filter((entry) -> entry.getKey().startsWith("xl/worksheets/"))
        .filter((entry) -> entry.getKey().endsWith(".xml"))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  private String cellText(Element cellElement, List<String> sharedStrings) {
    String type = cellElement.getAttribute("t");
    if ("inlineStr".equals(type)) {
      return nestedText(cellElement, "t");
    }

    String value = nestedText(cellElement, "v");
    if ("s".equals(type)) {
      int sharedStringIndex = new BigDecimal(value).intValueExact();
      if (sharedStringIndex < 0 || sharedStringIndex >= sharedStrings.size()) {
        throw new IllegalArgumentException("Invalid XLSX shared string reference");
      }
      return sharedStrings.get(sharedStringIndex);
    }
    if ("b".equals(type)) {
      return "1".equals(value) ? "true" : "false";
    }
    return value;
  }

  private String nestedText(Element element, String localName) {
    NodeList nodes = element.getElementsByTagNameNS(OPEN_XML_MAIN_NS, localName);
    if (nodes.getLength() == 0) {
      return "";
    }
    return nodes.item(0).getTextContent();
  }

  private String worksheetXml(List<List<String>> rows) {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
    xml.append("<worksheet xmlns=\"").append(OPEN_XML_MAIN_NS).append("\"><sheetData>");

    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      int rowNumber = rowIndex + 1;
      xml.append("<row r=\"").append(rowNumber).append("\">");
      List<String> row = rows.get(rowIndex);
      for (int columnIndex = 0; columnIndex < row.size(); columnIndex++) {
        String value = row.get(columnIndex);
        if (!value.isEmpty()) {
          appendCell(xml, columnIndex, rowNumber, COLUMNS.get(columnIndex), value);
        }
      }
      xml.append("</row>");
    }

    xml.append("</sheetData></worksheet>");
    return xml.toString();
  }

  private void appendCell(
      StringBuilder xml, int columnIndex, int rowNumber, String columnName, String value) {
    String cellReference = columnName(columnIndex) + rowNumber;
    xml.append("<c r=\"").append(cellReference).append("\"");
    if (BOOLEAN_COLUMNS.contains(columnName) && isBoolean(value)) {
      xml.append(" t=\"b\"><v>")
          .append(value.equalsIgnoreCase("true") || "1".equals(value) ? "1" : "0")
          .append("</v></c>");
    } else if (NUMERIC_COLUMNS.contains(columnName) && isNumeric(value)) {
      xml.append("><v>").append(value).append("</v></c>");
    } else {
      xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
          .append(escapeXml(value))
          .append("</t></is></c>");
    }
  }

  private String contentTypesXml() {
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
        </Types>
        """;
  }

  private String packageRelationshipsXml() {
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
        """;
  }

  private String workbookXml() {
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="financial-snapshot" sheetId="1" r:id="rId1"/>
          </sheets>
        </workbook>
        """;
  }

  private String workbookRelationshipsXml() {
    return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
        </Relationships>
        """;
  }

  private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(content.getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private String escapeCsv(String value) {
    if (value == null) {
      return "";
    }

    boolean needsQuotes =
        value.contains(",")
            || value.contains("\"")
            || value.contains("\n")
            || value.contains("\r")
            || value.startsWith(" ")
            || value.endsWith(" ");
    if (!needsQuotes) {
      return value;
    }

    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  private String escapeXml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private boolean isNumeric(String value) {
    try {
      new BigDecimal(value);
      return true;
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private boolean isBoolean(String value) {
    return value.equalsIgnoreCase("true")
        || value.equalsIgnoreCase("false")
        || "1".equals(value)
        || "0".equals(value);
  }

  private int columnIndex(String cellReference, int fallbackIndex) {
    if (cellReference == null || cellReference.isBlank()) {
      return fallbackIndex;
    }

    int index = 0;
    for (int offset = 0; offset < cellReference.length(); offset++) {
      char character = cellReference.charAt(offset);
      if (!Character.isLetter(character)) {
        break;
      }
      index = index * 26 + Character.toUpperCase(character) - 'A' + 1;
    }
    return Math.max(0, index - 1);
  }

  private String columnName(int zeroBasedIndex) {
    int index = zeroBasedIndex + 1;
    StringBuilder name = new StringBuilder();
    while (index > 0) {
      int remainder = (index - 1) % 26;
      name.insert(0, (char) ('A' + remainder));
      index = (index - 1) / 26;
    }
    return name.toString();
  }

  private int intAttribute(Element element, String attributeName, int fallback) {
    String value = element.getAttribute(attributeName);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return Integer.parseInt(value);
  }

  private IllegalArgumentException rowError(TabularRow row, String message) {
    return new IllegalArgumentException("Row " + row.rowNumber() + ": " + message);
  }

  private static List<String> emptyRow(String recordType) {
    List<String> row = new ArrayList<>();
    for (int index = 0; index < COLUMNS.size(); index++) {
      row.add("");
    }
    set(row, "recordType", recordType);
    return row;
  }

  private static void set(List<String> row, String columnName, String value) {
    row.set(COLUMN_INDEX.get(columnName), value == null ? "" : value);
  }

  private static String text(TabularRow row, String columnName) {
    return row.values().get(COLUMN_INDEX.get(columnName));
  }

  private static String requiredText(TabularRow row, String columnName) {
    String value = text(row, columnName);
    if (value.isBlank()) {
      throw new IllegalArgumentException("Row " + row.rowNumber() + ": Missing " + columnName);
    }
    return value;
  }

  private static Long optionalLong(TabularRow row, String columnName) {
    String value = text(row, columnName);
    if (value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value).longValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Row " + row.rowNumber() + ": Invalid integer in " + columnName, exception);
    }
  }

  private static Integer optionalInt(TabularRow row, String columnName) {
    String value = text(row, columnName);
    if (value.isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(value).intValueExact();
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Row " + row.rowNumber() + ": Invalid integer in " + columnName, exception);
    }
  }

  private static int requiredInt(TabularRow row, String columnName) {
    Integer value = optionalInt(row, columnName);
    if (value == null) {
      throw new IllegalArgumentException("Row " + row.rowNumber() + ": Missing " + columnName);
    }
    return value;
  }

  private static BigDecimal requiredDecimal(TabularRow row, String columnName) {
    String value = text(row, columnName);
    if (value.isBlank()) {
      throw new IllegalArgumentException("Row " + row.rowNumber() + ": Missing " + columnName);
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Row " + row.rowNumber() + ": Invalid decimal in " + columnName, exception);
    }
  }

  private static boolean requiredBoolean(TabularRow row, String columnName) {
    String value = text(row, columnName);
    if (value.equalsIgnoreCase("true") || "1".equals(value)) {
      return true;
    }
    if (value.equalsIgnoreCase("false") || "0".equals(value)) {
      return false;
    }
    throw new IllegalArgumentException(
        "Row " + row.rowNumber() + ": Invalid boolean in " + columnName);
  }

  private static LocalDate requiredDate(TabularRow row, String columnName) {
    String value = text(row, columnName);
    if (value.isBlank()) {
      throw new IllegalArgumentException("Row " + row.rowNumber() + ": Missing " + columnName);
    }
    try {
      return LocalDate.parse(value);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(
          "Row " + row.rowNumber() + ": Invalid ISO date in " + columnName, exception);
    }
  }

  private static String number(Long value) {
    return value == null ? "" : Long.toString(value);
  }

  private static String number(Integer value) {
    return value == null ? "" : Integer.toString(value);
  }

  private static String number(int value) {
    return Integer.toString(value);
  }

  private static String money(BigDecimal value) {
    return value == null ? "" : value.toPlainString();
  }

  private static String date(LocalDate value) {
    return value == null ? "" : value.toString();
  }

  private static <T> List<T> nullSafe(List<T> values) {
    return values == null ? List.of() : values;
  }

  private static boolean isBlank(List<String> values) {
    return values.stream().allMatch(String::isBlank);
  }

  private static Map<String, Integer> columnIndex() {
    Map<String, Integer> index = new LinkedHashMap<>();
    for (int offset = 0; offset < COLUMNS.size(); offset++) {
      index.put(COLUMNS.get(offset), offset);
    }
    return Map.copyOf(index);
  }

  private record TabularRow(int rowNumber, List<String> values) {}

  private static final class ParsedSnapshot {
    private Long version;
    private LocalDate payPeriodStart;
    private LocalDate payPeriodEnd;
    private final List<ExpenseBillSnapshotRequest> bills = new ArrayList<>();
    private final List<AnnualWithdrawalSnapshotRequest> annualWithdrawals = new ArrayList<>();
    private final Map<String, AssetCategoryBuilder> assetCategories = new LinkedHashMap<>();
    private final List<DebtAccountSnapshotRequest> debtAccounts = new ArrayList<>();
    private final List<IncomeSummaryItemSnapshotRequest> incomeSummaryItems = new ArrayList<>();
    private final List<IncomeEventSnapshotRequest> incomeEvents = new ArrayList<>();
    private final List<ImportantDateSnapshotRequest> importantDates = new ArrayList<>();

    private void readMetadata(TabularRow row) {
      if (version != null) {
        throw new IllegalArgumentException("Row " + row.rowNumber() + ": Duplicate snapshot row");
      }
      version = optionalLong(row, "version");
      payPeriodStart = requiredDate(row, "payPeriodStart");
      payPeriodEnd = requiredDate(row, "payPeriodEnd");
    }

    private void addAssetAccount(TabularRow row) {
      String categoryKey = text(row, "categoryKey");
      String categoryLabel = text(row, "categoryLabel");
      AssetCategoryBuilder category =
          assetCategories.computeIfAbsent(
              categoryKey, (key) -> new AssetCategoryBuilder(categoryKey, categoryLabel));
      if (!category.label().equals(categoryLabel)) {
        throw new IllegalArgumentException(
            "Row " + row.rowNumber() + ": Asset category label is inconsistent");
      }
      category
          .accounts()
          .add(
              new AssetAccountSnapshotRequest(
                  optionalLong(row, "id"),
                  text(row, "account"),
                  text(row, "company"),
                  requiredDecimal(row, "amount")));
    }

    private ExpenseSnapshotRequest toSnapshotRequest() {
      if (version == null || payPeriodStart == null || payPeriodEnd == null) {
        throw new IllegalArgumentException("Import is missing a snapshot row");
      }

      return new ExpenseSnapshotRequest(
          version,
          payPeriodStart,
          payPeriodEnd,
          List.copyOf(bills),
          List.copyOf(annualWithdrawals),
          assetCategories.values().stream().map(AssetCategoryBuilder::toRequest).toList(),
          List.copyOf(debtAccounts),
          List.copyOf(incomeSummaryItems),
          List.copyOf(incomeEvents),
          List.copyOf(importantDates));
    }
  }

  private record AssetCategoryBuilder(
      String key, String label, List<AssetAccountSnapshotRequest> accounts) {

    private AssetCategoryBuilder(String key, String label) {
      this(key, label, new ArrayList<>());
    }

    private AssetCategorySnapshotRequest toRequest() {
      return new AssetCategorySnapshotRequest(key, label, List.copyOf(accounts));
    }
  }
}
