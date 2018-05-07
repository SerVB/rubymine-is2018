package com.jetbrains.python.inspection

import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.PyBinaryExpression
import com.jetbrains.python.psi.PyBoolLiteralExpression
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.PyPrefixExpression

fun processBooleanConstantExpression(expression: PyExpression?): Boolean? {
    val unpackedExpression = expression.unpacked
    return when {
        unpackedExpression is PyBoolLiteralExpression ->
            handleBooleanLiteral(unpackedExpression)
        unpackedExpression.isBooleanBinaryNumericExpression ->
            handleBooleanBinaryNumericExpression(unpackedExpression as PyBinaryExpression)
        unpackedExpression.isBooleanPrefixExpression ->
            handleBooleanPrefixExpression(unpackedExpression as PyPrefixExpression)
        unpackedExpression.isBooleanBinaryBooleanExpression ->
            handleBooleanBinaryBooleanExpression(unpackedExpression as PyBinaryExpression)
        else -> null
    }
}

fun handleBooleanLiteral(expression: PyBoolLiteralExpression) = expression.value

fun handleBooleanBinaryNumericExpression(expression: PyBinaryExpression): Boolean? {
    val leftExpression = expression.leftExpression ?: return null
    val rightExpression = expression.rightExpression ?: return null

    val leftValue = processNumericConstantExpression(leftExpression) ?: return null
    val rightValue = processNumericConstantExpression(rightExpression) ?: return null

    return when (expression.operator) {
        PyTokenTypes.LT -> leftValue < rightValue
        PyTokenTypes.LE -> leftValue <= rightValue
        PyTokenTypes.GT -> leftValue > rightValue
        PyTokenTypes.GE -> leftValue >= rightValue
        PyTokenTypes.EQEQ -> leftValue == rightValue
        PyTokenTypes.NE, PyTokenTypes.NE_OLD -> leftValue != rightValue
        else -> null
    }
}

fun handleBooleanPrefixExpression(expression: PyPrefixExpression): Boolean? {
    val result = processBooleanConstantExpression(expression.operand) ?: return null
    if (expression.operator == PyTokenTypes.NOT_KEYWORD) {
        return !result
    }
    return null
}

fun handleBooleanBinaryBooleanExpression(expression: PyBinaryExpression): Boolean? {
    val leftExpression = expression.leftExpression ?: return null
    val rightExpression = expression.rightExpression ?: return null

    val leftValue = processBooleanConstantExpression(leftExpression) ?: return null
    val rightValue = processBooleanConstantExpression(rightExpression) ?: return null

    return when (expression.operator) {
        PyTokenTypes.AND_KEYWORD -> leftValue && rightValue
        PyTokenTypes.OR_KEYWORD -> leftValue || rightValue
        else -> null
    }
}

val PyExpression?.isBooleanBinaryNumericExpression get() =
    this is PyBinaryExpression && (this.operator == PyTokenTypes.LT ||
                                   this.operator == PyTokenTypes.LE ||
                                   this.operator == PyTokenTypes.GT ||
                                   this.operator == PyTokenTypes.GE ||
                                   this.operator == PyTokenTypes.EQEQ ||
                                   this.operator == PyTokenTypes.NE ||
                                   this.operator == PyTokenTypes.NE_OLD)

val PyExpression?.isBooleanPrefixExpression get() =
    this is PyPrefixExpression && this.operator == PyTokenTypes.NOT_KEYWORD

val PyExpression?.isBooleanBinaryBooleanExpression get() =
    this is PyBinaryExpression && (this.operator == PyTokenTypes.AND_KEYWORD ||
                                   this.operator == PyTokenTypes.OR_KEYWORD)
