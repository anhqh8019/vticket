import { test } from "node:test";
import assert from "node:assert/strict";
import { parseSmileCodes } from "./smile-import.js";
test("Excel paste keeps serials and leading-zero barcodes as strings", () => {
  assert.deepEqual(
    parseSmileCodes(
      "\uFEFFSeri\tbarcode\r\nVN000945\t0001234567890\r\nVN000946\t0001234567891\n",
    ),
    [
      { serial: "VN000945", barcode: "0001234567890" },
      { serial: "VN000946", barcode: "0001234567891" },
    ],
  );
});
test("semicolon and comma two-column text accepted", () => {
  assert.equal(parseSmileCodes("VN001;000001")[0].barcode, "000001");
  assert.equal(parseSmileCodes("VN002,000002")[0].serial, "VN002");
});
test("duplicates and malformed or incomplete rows are rejected", () => {
  for (const input of [
    "",
    "VN001\t123\nVN001\t456",
    "VN001\t123\nVN002\t123",
    "VN001",
    "VN001\t123\textra",
    "VN 001\t123",
  ])
    assert.throws(() => parseSmileCodes(input));
});
test("maximum batch size enforced", () => {
  assert.throws(
    () =>
      parseSmileCodes(
        Array.from({ length: 10001 }, (_, i) => `S${i}\tB${i}`).join("\n"),
      ),
    /10.000/,
  );
});

test("exact Smile SQL headers map SeqNo, PkgCode, dates and flags", () => {
  const row = parseSmileCodes(
    "SeqNo\tBarCode\tSerial\tPkgCode\tFromDate\tToDate\tIsWeekday\tIsChild\tIsCancel\tIsLoss\n1\t0001234567890\tVN005181\tHK\t2026-01-20 00:00:00.000\t2030-12-31 00:00:00.000\t0\t0\tNULL\tNULL",
  )[0];
  assert.equal(row.seqNo, "1");
  assert.equal(row.barcode, "0001234567890");
  assert.equal(row.serial, "VN005181");
  assert.equal(row.pkgCode, "HK");
  assert.equal(row.fromDate, "2026-01-20");
  assert.equal(row.isChild, false);
});
test("cancelled and lost Smile rows rejected before submission", () => {
  for (const flag of ["IsCancel", "IsLoss"])
    assert.throws(
      () => parseSmileCodes(`Serial\tBarCode\t${flag}\nVN001\t123\t1`),
      /hủy|mất/,
    );
});
