package com.jetbrains.python.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.psi.*

class PyConstantExpression : PyInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean,
                              session: LocalInspectionToolSession): PsiElementVisitor {
        return Visitor(holder, session)
    }

    class Visitor(holder: ProblemsHolder?, session: LocalInspectionToolSession) : PyInspectionVisitor(holder, session) {

        override fun visitPyIfStatement(node: PyIfStatement) {
            super.visitPyIfStatement(node)
            processIfPart(node.ifPart)
            for (part in node.elifParts) {
                processIfPart(part)
            }
        }

        private fun processIfPart(pyIfPart: PyIfPart) {
            val condition = pyIfPart.condition
            val result = processBooleanConstantExpression(condition) ?: return
            registerProblem(condition, "The condition is always $result")
        }

        private fun processBooleanConstantExpression(expression: PyExpression?): Boolean? {
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

        private fun handleBooleanLiteral(expression: PyBoolLiteralExpression) = expression.value

        private fun handleBooleanBinaryNumericExpression(expression: PyBinaryExpression): Boolean? {
            val leftExpression = expression.leftExpression.unpacked ?: return null
            val rightExpression = expression.rightExpression.unpacked ?: return null

            if (leftExpression is PyNumericLiteralExpression && rightExpression is PyNumericLiteralExpression) {
                /* TODO: The following check can be omitted if bigDecimalValue is used.
                   Also it will add Python float support.
                   But there can be some problems with Python float and BigDecimal precision incapability.
                   Is it needed? */
                if (leftExpression.isIntegerLiteral && rightExpression.isIntegerLiteral) {
                    /* TODO: Can leftExpression be null if it is already integer literal?
                       Maybe it is more correct to write this:
                       val leftValue = leftExpression.bigIntegerValue!! */
                    val leftValue = leftExpression.bigIntegerValue ?: return null
                    val rightValue = rightExpression.bigIntegerValue ?: return null

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
            }
            return null
        }

        private fun handleBooleanPrefixExpression(expression: PyPrefixExpression): Boolean? {
            val result = processBooleanConstantExpression(expression.operand) ?: return null
            return !result
        }

        private fun handleBooleanBinaryBooleanExpression(expression: PyBinaryExpression): Boolean? {
            val leftExpression = expression.leftExpression.unpacked ?: return null
            val rightExpression = expression.rightExpression.unpacked ?: return null

            if (leftExpression.isBooleanOperand && rightExpression.isBooleanOperand) {
                val leftValue = processBooleanConstantExpression(leftExpression) ?: return null
                val rightValue = processBooleanConstantExpression(rightExpression) ?: return null

                return when (expression.operator) {
                    PyTokenTypes.AND_KEYWORD -> leftValue && rightValue
                    PyTokenTypes.OR_KEYWORD -> leftValue || rightValue
                    else -> null
                }
            }
            return null
        }
    }
}

val PyExpression?.unpacked: PyExpression? get() {
    var answer = this
    while (answer is PyParenthesizedExpression) {
        answer = answer.containedExpression
    }
    return answer
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

val PyExpression?.isBooleanOperand get() =
        this is PyBoolLiteralExpression || this.isBooleanBinaryNumericExpression ||
        this.isBooleanPrefixExpression || this.isBooleanBinaryBooleanExpression
