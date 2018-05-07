package com.jetbrains.python.inspection

import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.PyPrefixExpression
import java.math.BigInteger

fun processNumericConstantExpression(expression: PyExpression?): BigInteger? {
    val unpackedExpression = expression.unpacked
    return when {
        unpackedExpression is PyNumericLiteralExpression ->
            handleNumericLiteral(unpackedExpression)
        unpackedExpression.isNumericBinaryNumericExpression ->
            handleNumericBinaryNumericExpression(unpackedExpression as PyBinaryExpression)
        unpackedExpression.isNumericPrefixExpression ->
            handleNumericPrefixExpression(unpackedExpression as PyPrefixExpression)
        else -> null
    }
}

fun handleNumericLiteral(expression: PyNumericLiteralExpression): BigInteger? {
    /* TODO: The following check can be omitted if bigDecimalValue is used.
       Also it will add Python float support.
       But there can be some problems with Python float and BigDecimal precision incapability.
       Is it needed? */
    if (expression.isIntegerLiteral) {
        return expression.bigIntegerValue
    }
    return null
}

fun handleNumericBinaryNumericExpression(expression: PyBinaryExpression): BigInteger? {
    val leftExpression = expression.leftExpression ?: return null
    val rightExpression = expression.rightExpression ?: return null

    val leftValue = processNumericConstantExpression(leftExpression) ?: return null
    val rightValue = processNumericConstantExpression(rightExpression) ?: return null

    return when (expression.operator) {
        PyTokenTypes.PLUS -> leftValue + rightValue
        PyTokenTypes.MINUS -> leftValue - rightValue
        PyTokenTypes.MULT -> leftValue * rightValue
        PyTokenTypes.FLOORDIV -> leftValue / rightValue
        PyTokenTypes.PERC -> leftValue % rightValue
        else -> null
    }
}

fun handleNumericPrefixExpression(expression: PyPrefixExpression): BigInteger? {
    val result = processNumericConstantExpression(expression.operand) ?: return null
    return when (expression.operator) {
        PyTokenTypes.PLUS -> result
        PyTokenTypes.MINUS -> -result
        else -> null
    }
}

val PyExpression?.isNumericBinaryNumericExpression get() =
    this is PyBinaryExpression && (this.operator == PyTokenTypes.PLUS ||
                                   this.operator == PyTokenTypes.MINUS ||
                                   this.operator == PyTokenTypes.MULT ||
                                   this.operator == PyTokenTypes.FLOORDIV ||
                                   this.operator == PyTokenTypes.PERC)

val PyExpression?.isNumericPrefixExpression get() =
    this is PyPrefixExpression && (this.operator == PyTokenTypes.PLUS ||
                                   this.operator == PyTokenTypes.MINUS)
