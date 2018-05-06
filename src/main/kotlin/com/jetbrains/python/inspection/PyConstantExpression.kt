package com.jetbrains.python.inspection

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiElement
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
            when (condition) {
                is PyBoolLiteralExpression -> handlePyBoolLiteralExpression(condition)
                is PyBinaryExpression -> handlePyBinaryExpression(condition)
            }
        }

        private fun registerBooleanProblem(element: PsiElement, value: Boolean) =
                registerProblem(element, "The condition is always $value")

        private fun handlePyBoolLiteralExpression(condition: PyBoolLiteralExpression) =
                registerBooleanProblem(condition, condition.value)

        private fun handlePyBinaryExpression(condition: PyBinaryExpression) {
            val leftExpression = condition.leftExpression ?: return
            val rightExpression = condition.rightExpression ?: return

            if (leftExpression is PyNumericLiteralExpression && rightExpression is PyNumericLiteralExpression) {
                if (leftExpression.isIntegerLiteral && rightExpression.isIntegerLiteral) {
                    /* TODO: Can leftExpression be null if it is already integer integral?
                       Maybe it is more correct to write this:
                       val leftValue = leftExpression.bigIntegerValue!! */
                    val leftValue = leftExpression.bigIntegerValue ?: return
                    val rightValue = rightExpression.bigIntegerValue ?: return

                    when (condition.operator) {
                        PyTokenTypes.LT -> registerBooleanProblem(condition, leftValue < rightValue)
                        PyTokenTypes.LE -> registerBooleanProblem(condition, leftValue <= rightValue)
                        PyTokenTypes.GT -> registerBooleanProblem(condition, leftValue > rightValue)
                        PyTokenTypes.GE -> registerBooleanProblem(condition, leftValue >= rightValue)
                        PyTokenTypes.EQEQ -> registerBooleanProblem(condition, leftValue == rightValue)
                        PyTokenTypes.NE, PyTokenTypes.NE_OLD ->
                            registerBooleanProblem(condition, leftValue != rightValue)
                    }
                }
            }
        }
    }
}
