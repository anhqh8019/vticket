export function comboQuantityError(product, quantity) {
  if (!quantity) return "";
  if (!Number.isInteger(quantity) || quantity < 0 || quantity > 1000)
    return `${product.name}: số lượng phải là số nguyên từ 1 đến 1.000.`;
  if (product.kind !== "COMBO") return "";
  const step = product.comboBuyQuantity;
  if (!product.comboTicketId || !Number.isInteger(step) || step < 1)
    return `${product.name}: combo chưa được cấu hình đầy đủ.`;
  return quantity < step || quantity % step !== 0
    ? `${product.name}: cần nhập số vé là bội số của ${step} (ví dụ ${step}, ${step * 2}).`
    : "";
}
export function comboTicketCount(product, quantity) {
  if (comboQuantityError(product, quantity)) return 0;
  return product.kind === "COMBO"
    ? quantity +
        (quantity / product.comboBuyQuantity) * (product.comboGiftQuantity || 0)
    : quantity;
}
