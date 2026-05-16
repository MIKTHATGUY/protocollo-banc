package org.example.bancariofaccia.protocol.PreciseMoney;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.AttributedCharacterIterator;
import java.text.NumberFormat;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static java.math.BigDecimal.ZERO;

/**
 * Represents an amount of money tied to a specific Locale.
 *
 * <P>{@code Money} objects are immutable. All arithmetic rounds to the standard
 * decimal places of the Locale's currency using {@link RoundingMode#HALF_EVEN} (banker's rounding).
 *
 * <h2>Locale over Currency</h2>
 * A {@link Locale} must be provided upon instantiation (e.g., {@code Locale.ITALY}).
 * The underlying {@link Currency} (e.g., EUR) is automatically derived from this Locale,
 * and handles both the mathematical scale and human-readable string formatting.
 */
public final class Money implements Comparable<Money>, Serializable {

    private static final long serialVersionUID = 7526471155622776148L;

    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    // -------------------------------------------------------------------------
    // Inner exception
    // -------------------------------------------------------------------------

    /**
     * Thrown when two {@code Money} objects do not have matching underlying currencies.
     */
    public static final class MismatchedCurrencyException extends RuntimeException {
        MismatchedCurrencyException(String message) {
            super(message);
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * @serial
     */
    private BigDecimal amount;  // non-final only to allow defensive copy in readObject

    /**
     * @serial
     */
    private final Locale locale;

    /**
     * @serial
     */
    private final Currency currency;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Constructs a Money object using the given Locale.
     *
     * @param amount required; scale must not exceed the locale currency's default fraction digits.
     * @param locale required; must contain a country code so a Currency can be resolved (e.g., Locale.ITALY).
     */
    public Money(BigDecimal amount, Locale locale) {
        this.amount = Objects.requireNonNull(amount, "amount");
        this.locale = Objects.requireNonNull(locale, "locale");

        try {
            this.currency = Currency.getInstance(locale);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The provided locale (" + locale + ") does not have an associated currency.", e);
        }

        validateState();
    }

    // -------------------------------------------------------------------------
    // Factory — high-precision input
    // -------------------------------------------------------------------------

    /**
     * Creates a Money in the given Locale by rounding {@code rawAmount} to the currency's
     * default fraction digits. Use this when the input comes from intermediate calculations
     * with excess precision.
     */
    public static Money ofRounded(BigDecimal rawAmount, Locale locale) {
        Currency cur = Currency.getInstance(locale);
        return new Money(rawAmount.setScale(cur.getDefaultFractionDigits(), ROUNDING), locale);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public BigDecimal getAmount() {
        return amount;
    }

    public Locale getLocale() {
        return locale;
    }

    public Currency getCurrency() {
        return currency;
    }

    // -------------------------------------------------------------------------
    // Predicates
    // -------------------------------------------------------------------------

    public boolean isSameCurrencyAs(Money that) {
        return that != null && currency.equals(that.currency);
    }

    public boolean isPlus() {
        return amount.compareTo(ZERO) > 0;
    }

    public boolean isMinus() {
        return amount.compareTo(ZERO) < 0;
    }

    public boolean isZero() {
        return amount.compareTo(ZERO) == 0;
    }

    // -------------------------------------------------------------------------
    // Arithmetic — addition / subtraction
    // -------------------------------------------------------------------------

    /**
     * Returns {@code this + that}. Underlying currencies must match.
     * The resulting Money retains the Locale of the left-hand operand.
     */
    public Money plus(Money that) {
        checkCurrenciesMatch(that);
        return new Money(amount.add(that.amount), locale);
    }

    /**
     * Returns {@code this - that}. Underlying currencies must match.
     * The resulting Money retains the Locale of the left-hand operand.
     */
    public Money minus(Money that) {
        checkCurrenciesMatch(that);
        return new Money(amount.subtract(that.amount), locale);
    }

    /**
     * Sums a collection of Money objects.
     *
     * @param localeIfEmpty locale to use when the collection is empty
     */
    public static Money sum(Collection<Money> moneys, Locale localeIfEmpty) {
        Money total = new Money(ZERO, localeIfEmpty);
        for (Money m : moneys) total = total.plus(m);
        return total;
    }

    // -------------------------------------------------------------------------
    // Arithmetic — multiplication
    // -------------------------------------------------------------------------

    /**
     * Returns {@code this * factor}, rounded to the currency's decimal places.
     */
    public Money times(int factor) {
        return new Money(
                amount.multiply(BigDecimal.valueOf(factor))
                        .setScale(currency.getDefaultFractionDigits(), ROUNDING),
                locale);
    }

    /**
     * Returns {@code this * factor}, rounded to the currency's decimal places.
     */
    public Money times(BigDecimal factor) {
        Objects.requireNonNull(factor, "factor");
        return new Money(
                amount.multiply(factor)
                        .setScale(currency.getDefaultFractionDigits(), ROUNDING),
                locale);
    }

    // -------------------------------------------------------------------------
    // Arithmetic — division
    // -------------------------------------------------------------------------

    /**
     * Returns {@code this / divisor}, rounded to the currency's decimal places.
     */
    public Money div(int divisor) {
        if (divisor == 0) throw new ArithmeticException("Division by zero");
        return new Money(
                amount.divide(BigDecimal.valueOf(divisor), currency.getDefaultFractionDigits(), ROUNDING),
                locale);
    }

    /**
     * Returns {@code this / divisor}, rounded to the currency's decimal places.
     */
    public Money div(BigDecimal divisor) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.compareTo(ZERO) == 0) throw new ArithmeticException("Division by zero");
        return new Money(
                amount.divide(divisor, currency.getDefaultFractionDigits(), ROUNDING),
                locale);
    }

    // -------------------------------------------------------------------------
    // Utility arithmetic
    // -------------------------------------------------------------------------

    public Money abs() {
        return isMinus() ? negate() : this;
    }

    public Money negate() {
        return new Money(amount.negate(), locale);
    }

    // -------------------------------------------------------------------------
    // Comparisons (scale-insensitive)
    // -------------------------------------------------------------------------

    public boolean eq(Money that) {
        checkCurrenciesMatch(that);
        return compareAmount(that) == 0;
    }

    public boolean gt(Money that) {
        checkCurrenciesMatch(that);
        return compareAmount(that) > 0;
    }

    public boolean gteq(Money that) {
        checkCurrenciesMatch(that);
        return compareAmount(that) >= 0;
    }

    public boolean lt(Money that) {
        checkCurrenciesMatch(that);
        return compareAmount(that) < 0;
    }

    public boolean lteq(Money that) {
        checkCurrenciesMatch(that);
        return compareAmount(that) <= 0;
    }

    // -------------------------------------------------------------------------
    // Object overrides & Formatting
    // -------------------------------------------------------------------------

    /**
     * Returns a locale-aware human readable string, e.g. {@code "10,50 €"} or {@code "1.5M €"}.
     */
    @Override
    public String toString() {
        return asHumanReadableString();
    }

    public String asHumanReadableString() {
        BigDecimal absAmount = amount.abs();

        // Mathematically calculate the magnitude to support infinitely large payload numbers safely
        int digits = absAmount.precision() - absAmount.scale();
        int magnitude = digits > 0 ? (digits - 1) / 3 : 0;

        String[] suffixes = {"", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};
        String suffix = "";

        if (magnitude > 0) {
            if (magnitude < suffixes.length) {
                suffix = suffixes[magnitude];
            } else {
                // Failsafe for astronomically large numbers (e.g. 8000-digit packet payloads)
                suffix = "E" + (magnitude * 3);
            }
        }

        BigDecimal scaledAmount = amount;
        if (magnitude > 0) {
            BigDecimal divisor = BigDecimal.TEN.pow(magnitude * 3);
            scaledAmount = amount.divide(divisor, 2, ROUNDING).stripTrailingZeros();
        }

        NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
        formatter.setCurrency(currency);

        if (magnitude > 0) {
            // Drop decimals for clean K/M/B like "1M" or "1,5M"
            formatter.setMinimumFractionDigits(0);
            formatter.setMaximumFractionDigits(2);
        } else {
            // Standard currency formatting
            formatter.setMinimumFractionDigits(currency.getDefaultFractionDigits());
            formatter.setMaximumFractionDigits(currency.getDefaultFractionDigits());
        }

        String formatted = formatter.format(scaledAmount);

        if (magnitude == 0) {
            return formatted;
        }

        // Accurately insert the suffix right after the numeric part (before currency symbols or spaces)
        AttributedCharacterIterator iterator = formatter.formatToCharacterIterator(scaledAmount);
        int lastNumericIndex = -1;

        for (char c = iterator.first(); c != AttributedCharacterIterator.DONE; c = iterator.next()) {
            Set<AttributedCharacterIterator.Attribute> attrs = iterator.getAttributes().keySet();
            if (attrs.contains(NumberFormat.Field.INTEGER) || attrs.contains(NumberFormat.Field.FRACTION)) {
                lastNumericIndex = iterator.getIndex();
            }
        }

        if (lastNumericIndex != -1) {
            int insertIndex = lastNumericIndex + 1;
            return formatted.substring(0, insertIndex) + suffix + formatted.substring(insertIndex);
        }

        return formatted + suffix; // Fallback
    }


    public String asPreciseString() {

        NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
        formatter.setCurrency(currency);


        return formatter.format(amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money that)) return false;
        return Objects.equals(amount, that.amount) && Objects.equals(locale, that.locale);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, locale);
    }

    @Override
    public int compareTo(Money that) {
        int cmp = this.amount.compareTo(that.amount);
        if (cmp != 0) return cmp;
        return this.currency.getCurrencyCode().compareTo(that.currency.getCurrencyCode());
    }

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------

    @Serial
    private void readObject(ObjectInputStream in) throws ClassNotFoundException, IOException {
        in.defaultReadObject();
        amount = new BigDecimal(amount.toPlainString()); // defensive copy
        validateState();
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    // -------------------------------------------------------------------------
    // ByteBuffer serialization
    // -------------------------------------------------------------------------

    /**
     * Serialises this Money to a {@link ByteBuffer}.
     *
     * <p>Layout:
     * <pre>
     * [totalLen        : INT32]
     * [localeTagLen    : INT32][localeTag : UTF-8]
     * [amountStr       : UTF-8]  (remainder)
     * </pre>
     */
    public ByteBuffer asByteBuffer() {
        byte[] localeBytes = locale.toLanguageTag().getBytes(StandardCharsets.UTF_8);
        byte[] amountBytes = amount.toPlainString().getBytes(StandardCharsets.UTF_8);

        int totalLen = 4 + localeBytes.length + amountBytes.length;
        ByteBuffer buf = ByteBuffer.allocate(4 + totalLen);
        buf.putInt(totalLen);
        buf.putInt(localeBytes.length);
        buf.put(localeBytes);
        buf.put(amountBytes);
        buf.flip();
        return buf;
    }

    /**
     * Deserialises a Money from a {@link ByteBuffer} produced by {@link #asByteBuffer()}.
     */
    public static Money fromByteBuffer(ByteBuffer buf) {
        buf.getInt(); // skip totalLen

        byte[] localeBytes = new byte[buf.getInt()];
        buf.get(localeBytes);
        Locale loc = Locale.forLanguageTag(new String(localeBytes, StandardCharsets.UTF_8));

        byte[] amountBytes = new byte[buf.remaining()];
        buf.get(amountBytes);

        return new Money(new BigDecimal(new String(amountBytes, StandardCharsets.UTF_8)), loc);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void validateState() {
        if (amount.scale() > currency.getDefaultFractionDigits()) {
            throw new IllegalArgumentException(
                    "Amount scale " + amount.scale() +
                            " exceeds the " + currency.getDefaultFractionDigits() +
                            " decimal places allowed for " + currency.getCurrencyCode() +
                            ". Use Money.ofRounded(BigDecimal, Locale) to round first.");
        }
    }

    private void checkCurrenciesMatch(Money that) {
        if (!this.currency.equals(that.currency))
            throw new MismatchedCurrencyException(
                    "Currency " + that.currency + " (from Locale " + that.locale +
                            ") doesn't match expected currency: " + currency + " (from Locale " + locale + ")");
    }

    private int compareAmount(Money that) {
        return this.amount.compareTo(that.amount);
    }
}