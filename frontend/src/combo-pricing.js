export function dayType(date, holidays = []) {
  if (holidays.some(day => day.date === date)) return 'HOLIDAY';
  const day = new Date(`${date}T00:00:00Z`).getUTCDay();
  return day === 0 || day === 6 ? 'WEEKEND' : 'WEEKDAY';
}
export function priceForDate(product, date, holidays = []) {
  if (product.kind !== 'COMBO' && product.kind !== 'TICKET') return Number(product.price);
  const type = dayType(date, holidays);
  if (product.child) {
    if (product.childPrice == null) return null;
    if (type === 'HOLIDAY' && product.childHolidayPrice != null) return Number(product.childHolidayPrice);
    if (type === 'WEEKEND' && product.childWeekendPrice != null) return Number(product.childWeekendPrice);
    return Number(product.childPrice);
  }
  if (type === 'HOLIDAY' && product.holidayPrice != null) return Number(product.holidayPrice);
  if (type === 'WEEKEND' && product.weekendPrice != null) return Number(product.weekendPrice);
  return Number(product.price);
}
