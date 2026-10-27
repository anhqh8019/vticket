// Preserve Smile identifiers as text. Full SSMS/Excel headers may be supplied in any order.
export function parseSmileCodes(text) {
  const rows = [],
    serials = new Set(),
    barcodes = new Set();
  let headers;
  const bool = (value, name) => {
    if (value == null || value === "" || value.toLowerCase() === "null")
      return undefined;
    if (/^(0|false)$/i.test(value)) return false;
    if (/^(1|true)$/i.test(value)) return true;
    throw new Error(`${name} phải là 0 hoặc 1.`);
  };
  const date = (value, name) => {
    if (!value) return undefined;
    const match = value.match(
      /^(\d{4}-\d{2}-\d{2})(?:[ T]00:00:00(?:\.0+)?)?$/,
    );
    if (!match || new Date(match[1]).toISOString().slice(0, 10) !== match[1])
      throw new Error(`${name} cần ngày ISO YYYY-MM-DD (thời gian 00:00:00).`);
    return match[1];
  };
  for (const [index, line] of text
    .replace(/^\uFEFF/, "")
    .split(/\r?\n/)
    .entries()) {
    if (!line.trim()) continue;
    const delimiter = line.includes("\t")
      ? "\t"
      : line.includes(";")
        ? ";"
        : ",";
    const cells = line.split(delimiter).map((s) => s.trim());
    const names = cells.map((s) =>
      s.toLowerCase() === "seri" ? "serial" : s.toLowerCase(),
    );
    if (
      !rows.length &&
      !headers &&
      names.includes("serial") &&
      names.includes("barcode")
    ) {
      if (new Set(names).size !== names.length)
        throw new Error("Tên cột bị trùng.");
      headers = names;
      continue;
    }
    try {
      if (
        (headers && cells.length !== headers.length) ||
        (!headers && ![2, 3].includes(cells.length))
      )
        throw new Error(
          "Số cột không khớp; cần Seri–barcode hoặc SeqNo–Serial–BarCode.",
        );
      const values = Object.fromEntries(
        (
          headers ||
          (cells.length === 3
            ? ["seqno", "serial", "barcode"]
            : ["serial", "barcode"])
        ).map((name, i) => [name, cells[i]]),
      );
      const { serial, barcode } = values;
      if (
        !/^[A-Za-z0-9._/-]{1,64}$/.test(serial || "") ||
        !/^[A-Za-z0-9._/-]{1,128}$/.test(barcode || "")
      )
        throw new Error("Seri / barcode dạng văn bản không hợp lệ.");
      if (
        serials.has(serial.toUpperCase()) ||
        barcodes.has(barcode.toUpperCase())
      )
        throw new Error("Seri hoặc barcode bị trùng.");
      const row = { serial, barcode };
      if (values.seqno) {
        if (!/^\d{1,19}$/.test(values.seqno))
          throw new Error("SeqNo phải là số nguyên tối đa 19 chữ số.");
        row.seqNo = values.seqno;
      }
      if ("pkgcode" in values) {
        if (!/^[A-Za-z0-9._/-]{1,64}$/.test(values.pkgcode))
          throw new Error("PkgCode không hợp lệ.");
        row.pkgCode = values.pkgcode;
      }
      for (const [key, name] of [
        ["fromDate", "fromdate"],
        ["toDate", "todate"],
      ])
        if (name in values) {
          row[key] = date(values[name], name);
          if (!row[key]) throw new Error(`${name} không được trống.`);
        }
      for (const [key, name] of [
        ["isWeekday", "isweekday"],
        ["isChild", "ischild"],
        ["isCancel", "iscancel"],
        ["isLoss", "isloss"],
      ])
        if (name in values) row[key] = bool(values[name], name);
      if (row.isCancel || row.isLoss)
        throw new Error("Vé đã hủy hoặc báo mất trên Smile không được nhập.");
      serials.add(serial.toUpperCase());
      barcodes.add(barcode.toUpperCase());
      rows.push(row);
      if (rows.length > 10000) throw new Error("Mỗi lô tối đa 10.000 vé.");
    } catch (e) {
      throw new Error(`Dòng ${index + 1}: ${e.message}`);
    }
  }
  if (!rows.length) throw new Error("Chưa nhập cặp Seri / barcode nào.");
  return rows;
}
