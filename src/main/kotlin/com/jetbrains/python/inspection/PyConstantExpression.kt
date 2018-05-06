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
            val result = processConstantExpression(condition) ?: return
            registerProblem(condition, "The condition is always $result")
        }

        private fun processConstantExpression(expression: PyExpression?): Boolean? {
            val unpackedExpression = expression.unpacked
            return when {
                unpackedExpression is PyBoolLiteralExpression ->
                    handlePyBoolLiteralExpression(unpackedExpression)
                unpackedExpression.isNumericExpression ->
                    handlePyBinaryExpression(unpackedExpression as PyBinaryExpression)
                else -> null
            }
        }

        private fun handlePyBoolLiteralExpression(expression: PyBoolLiteralExpression) = expression.value

        private fun handlePyBinaryExpression(expression: PyBinaryExpression): Boolean? {
            val leftExpression = expression.leftExpression.unpacked ?: return null
            val rightExpression = expression.rightExpression.unpacked ?: return null

            if (leftExpression is PyNumericLiteralExpression && rightExpression is PyNumericLiteralExpression) {
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
    }
}

val PyExpression?.unpacked: PyExpression? get() {
    var answer = this
    while (answer is PyParenthesizedExpression) {
        answer = answer.containedExpression
    }
    return answer
}

val PyExpression?.isNumericExpression get() =
        this is PyBinaryExpression && (this.operator == PyTokenTypes.LT ||
                                       this.operator == PyTokenTypes.LE ||
                                       this.operator == PyTokenTypes.GT ||
                                       this.operator == PyTokenTypes.GE ||
                                       this.operator == PyTokenTypes.EQEQ ||
                                       this.operator == PyTokenTypes.NE ||
                                       this.operator == PyTokenTypes.NE_OLD)
