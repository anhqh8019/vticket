import { test } from "node:test";
import assert from "node:assert/strict";
import { comboQuantityError, comboTicketCount } from "./combo.js";
const combo = {
  name: "Mua 10 tặng 1",
  kind: "COMBO",
  comboTicketId: 1,
  comboBuyQuantity: 10,
  comboGiftQuantity: 1,
};
test("combo requires complete groups and rejects fractional quantities", () => {
  for (const qty of [1, 9, 11, 19, 1.5, 1001])
    assert.ok(comboQuantityError(combo, qty));
  for (const qty of [0, 10, 20, 100])
    assert.equal(comboQuantityError(combo, qty), "");
});
test("gift ticket counts scale with completed groups", () => {
  assert.equal(comboTicketCount(combo, 10), 11);
  assert.equal(comboTicketCount(combo, 20), 22);
  assert.equal(comboTicketCount(combo, 9), 0);
});
test("ordinary tickets and unconfigured combos are handled", () => {
  assert.equal(comboTicketCount({ kind: "TICKET" }, 3), 3);
  assert.ok(comboQuantityError({ ...combo, comboTicketId: null }, 10));
});

test("dealer combos accept 300 and 500 but still require complete groups", () => {
  for (const size of [300, 500]) {
    const dealer = { ...combo, comboBuyQuantity: size, comboGiftQuantity: 0 };
    assert.equal(comboQuantityError(dealer, size), "");
    assert.equal(comboTicketCount(dealer, size), size);
    assert.ok(comboQuantityError(dealer, size - 1));
  }
});
