/**********************************************************************
Copyright (c) 2008 Erik Bengtson and others. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Contributors:
2008 Andy Jefferson - much restructuring, static methods, negate/complement operators
2009 Andy Jefferson - from clause, result clause, cast expression
    ...
**********************************************************************/
package org.datanucleus.query.compiler;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import org.datanucleus.exceptions.NucleusException;
import org.datanucleus.exceptions.NucleusUserException;
import org.datanucleus.query.JDOQLQueryHelper;
import org.datanucleus.store.query.QueryCompilerSyntaxException;
import org.datanucleus.util.Localiser;
import org.datanucleus.util.StringUtils;

/**
 * Implementation of a parser for JDOQL query language.
 * Generates Node tree(s) by use of the various parseXXX() methods.
 */
public class JDOQLParser extends AbstractParser
{
    private static String[] jdoqlMethodNames = {"contains", "get", "containsKey", "containsValue", "isEmpty", // Containers
        "size", "toLowerCase", "toUpperCase", "indexOf", "matches", "substring", "startsWith", "endsWith", "trim", "length", // String
        "getObjectId", "abs", "sqrt",
        "getDate", "getMonth", "getYear", "getHour", "getMinute", "getSecond", "getDayOfMonth", "getMonthValue", "getDays", "getMonths", "getYears", // Temporal
        "ordinal", "toString", // Enum
        "isPresent", "get", "orElse", // Optional
    };

    private boolean explicitParams = false;

    /** Characters that parameters can be prefixed by. */
    private static String paramPrefixes = ":";

    private boolean allowSingleEquals = false;

    /**
     * Constructor for a JDOQL Parser.
     */
    public JDOQLParser()
    {
    }

    /* (non-Javadoc)
     * @see org.datanucleus.query.compiler.AbstractParser#setExplicitParameters(boolean)
     */
    @Override
    public void setExplicitParameters(boolean flag)
    {
        this.explicitParams = flag;
    }

    /**
     * Method to set whether "=" is allowed. It is valid in an UPDATE clause, but not in a WHERE clause, for example.
     * @param flag Whether to allow it
     */
    public void allowSingleEquals(boolean flag)
    {
        allowSingleEquals = flag;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.query.compiler.Parser#parse(java.lang.String)
     */
    public Node parse(String expression)
    {
        lexer = new Lexer(expression, paramPrefixes, true);
        stack = new ArrayDeque<Node>();
        Node result = processExpression();

        if (lexer.ci.getIndex() != lexer.ci.getEndIndex())
        {
            // Error occurred in the JDOQL processing due to syntax error(s)
            String unparsed = lexer.getInput().substring(lexer.ci.getIndex());
            throw new QueryCompilerSyntaxException("Portion of expression could not be parsed: " + unparsed);
        }
        return result;
    }
    
    /* (non-Javadoc)
     * @see org.datanucleus.query.compiler.Parser#parseVariable(java.lang.String)
     */
    public Node parseVariable(String expression)
    {
        lexer = new Lexer(expression, paramPrefixes, true);
        stack = new ArrayDeque<Node>();
        if (!processIdentifier())
        {
            throw new QueryCompilerSyntaxException("expected identifier", lexer.getIndex(), lexer.getInput());
        }
        if (!processIdentifier())
        {
            throw new QueryCompilerSyntaxException("expected identifier", lexer.getIndex(), lexer.getInput());
        }
        Node nodeVariable = stack.pop();
        Node nodeType = stack.pop();
        nodeType.appendChildNode(nodeVariable);
        return nodeType;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.query.compiler.Parser#parseFrom(java.lang.String)
     */
    public Node[] parseFrom(String expression)
    {
        lexer = new Lexer(expression, paramPrefixes, true);
        stack = new ArrayDeque<Node>();
        return processFromExpression();
    }

    /**
     * The FROM expression in JDOQL (subquery) is a "candidate alias" expression, like
     * <ul>
     * <li>mydomain.MyClass [AS] alias"</li>
     * </ul>
     * @return Node tree(s) for the FROM expression
     */
    private Node[] processFromExpression()
    {
        // "candidate [AS] alias"
        // This will create a node of type "Node.CLASS" and child of type "Node.NAME" (alias)
        processExpression();
        Node id = stack.pop();
        StringBuilder className = new StringBuilder(id.getNodeValue().toString());
        while (!id.getChildNodes().isEmpty())
        {
            id = id.getFirstChild();
            className.append(".").append(id.getNodeValue().toString());
        }

        String alias = lexer.parseIdentifier();
        if (alias != null && alias.equalsIgnoreCase("AS"))
        {
            alias = lexer.parseIdentifier();
        }
        if (alias == null)
        {
            alias = "this";
        }

        // Create candidate class node with alias, and put at top of stack
        Node classNode = new Node(NodeType.CLASS, className.toString());
        Node aliasNode = new Node(NodeType.NAME, alias);
        classNode.insertChildNode(aliasNode);
        stack.push(classNode);

        return new Node[] {classNode};
    }

    /* (non-Javadoc)
     * @see org.datanucleus.query.compiler.Parser#parseUpdate(java.lang.String)
     */
    public Node[] parseUpdate(String expression)
    {
        // Bulk update not supported by JDOQL
        return null;
    }

    /* (non-Javadoc)
     * @see org.datanucleus.query.compiler.Parser#parseOrder(java.lang.String)
     */
    public Node[] parseOrder(String expression)
    {
        lexer = new Lexer(expression, paramPrefixes, true);
        stack = new ArrayDeque<Node>();
        return processOrderExpression();
    }

    /**
     * The RESULT expression in JDOQL can include aggregates, fields, as well as aliases
     * <ul>
     * <li>myfield [AS] alias, myfield2"</li>
     * </ul>
     * The Node tree for this would be
     * <pre>[
     * [IDENTIFIER : myfield.
     *      [NAME : alias]],
     * [IDENTIFIER : myfield2]
     * ]</pre>
     * @return Node tree(s) for the RESULT expression
     */
    public Node[] parseResult(String expression)
    {
        lexer = new Lexer(expression, paramPrefixes, true);
        stack = new ArrayDeque<Node>();
        List nodes = new ArrayList();
        do
        {
            processExpression();
            Node expr = stack.pop();

            String alias = lexer.parseIdentifier();
            if (alias != null && alias.equalsIgnoreCase("AS"))
            {
                alias = lexer.parseIdentifier();
            }
            if (alias != null)
            {
                Node aliasNode = new Node(NodeType.NAME, alias);
                expr.appendChildNode(aliasNode);
            }

            nodes.add(expr);
        }
        while (lexer.parseString(","));
        return (Node[])nodes.toArray(new Node[nodes.size()]);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.query.compiler.Parser#parseTuple(java.lang.String)
     */
    public Node[] parseTuple(String expression)
    {
        lexer = new Lexer(expression, paramPrefixes, true);
        stack = new ArrayDeque<Node>();
        List nodes = new ArrayList();
        do
        {
            processExpression();
            Node expr = stack.pop();
            nodes.add(expr);
        }
        while (lexer.parseString(","));
        return (Node[])nodes.toArray(new Node[nodes.size()]);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.query.compiler.Parser#parseVariables(java.lang.String)
     */
    public Node[][] parseVariables(String expression)
    {
        lexer = new Lexer(expression, paramPrefixes, true);
        List nodes = new ArrayList();
        do
        {
            if (StringUtils.isWhitespace(lexer.remaining()))
            {
                break;
            }

            processPrimary();
            if (stack.isEmpty())
            {
                throw new QueryCompilerSyntaxException("Parsing variable list and expected variable type", 
                    lexer.getIndex(), lexer.getInput());
            }
            if (!processIdentifier())
            {
                throw new QueryCompilerSyntaxException("Parsing variable list and expected variable name",
                    lexer.getIndex(), lexer.getInput());
            }

            Node nodeVariable = stack.pop();
            String varName = (String)nodeVariable.getNodeValue();
            if (!JDOQLQueryHelper.isValidJavaIdentifierForJDOQL(varName))
            {
                throw new NucleusUserException(Localiser.msg("021105",varName));
            }

            Node nodeType = stack.pop();
            nodes.add(new Node[]{nodeType, nodeVariable});
        }
        while (lexer.parseString(";"));
        return (Node[][]) nodes.toArray(new Node[nodes.size()][2]);
    }

    /* (non-Javadoc)
     * @see org.datanucleus.query.compiler.Parser#parseParameters(java.lang.String)
     */
    public Node[][] parseParameters(String expression)
    {
        List nodes = new ArrayList();
        StringTokenizer tokeniser = new StringTokenizer(expression, ",");
        while (tokeniser.hasMoreTokens())
        {
            String token = tokeniser.nextToken();
            StringTokenizer subTokeniser = new StringTokenizer(token, " ");
            if (subTokeniser.countTokens() != 2)
            {
                throw new QueryCompilerSyntaxException(Localiser.msg("021101", expression));
            }
            String classDecl = subTokeniser.nextToken();
            String parameterName = subTokeniser.nextToken();
            Node declNode = new Node(NodeType.IDENTIFIER, classDecl);
            Node nameNode = new Node(NodeType.IDENTIFIER, parameterName);
            nodes.add(new Node[]{declNode, nameNode});
        }

        return (Node[][]) nodes.toArray(new Node[nodes.size()][2]);
    }

    private Node[] processOrderExpression()
    {
        List nodes = new ArrayList();
        do
        {
            processExpression();
            Node directionNode = null;
            if (lexer.parseString("ascending") || lexer.parseString("asc") ||
                lexer.parseString("ASCENDING") || lexer.parseString("ASC"))
            {
                directionNode = new Node(NodeType.OPERATOR, "ascending");
            }
            else if (lexer.parseString("descending") || lexer.parseString("desc") ||
                     lexer.parseString("DESCENDING") || lexer.parseString("DESC"))
            {
                directionNode = new Node(NodeType.OPERATOR, "descending");
            }
            else
            {
                // Default to ascending
                directionNode = new Node(NodeType.OPERATOR, "ascending");
            }

            // Nulls positioning
            Node nullsNode = null;
            if (lexer.parseString("NULLS FIRST") || lexer.parseString("nulls first"))
            {
                nullsNode = new Node(NodeType.OPERATOR, "nulls first");
            }
            else if (lexer.parseString("NULLS LAST") || lexer.parseString("nulls last"))
            {
                nullsNode = new Node(NodeType.OPERATOR, "nulls last");
            }

            Node expr = new Node(NodeType.OPERATOR, "order");
            expr.insertChildNode(directionNode);
            if (nullsNode != null)
            {
                expr.appendChildNode(nullsNode);
            }

            if (!stack.isEmpty()) // How can this be not empty?
            {
                expr.insertChildNode(stack.pop());
            }
            nodes.add(expr);
        }
        while (lexer.parseChar(','));

        return (Node[]) nodes.toArray(new Node[nodes.size()]);
    }

    private Node processExpression()
    {
        processConditionalOrExpression();
        return stack.peek();
    }

    /**
     * This method deals with the OR condition.
     * A condition specifies a combination of one or more expressions and logical (Boolean) operators and 
     * returns a value of TRUE, FALSE, or unknown
     */
    private void processConditionalOrExpression()
    {
        processConditionalAndExpression();

        while (lexer.parseString("||"))
        {
            processConditionalAndExpression();
            Node expr = new Node(NodeType.OPERATOR, "||");
            expr.insertChildNode(stack.pop());
            expr.insertChildNode(stack.pop());
            stack.push(expr);
        }
    }

    /**
     * This method deals with the AND condition.
     * A condition specifies a combination of one or more expressions and
     * logical (Boolean) operators and returns a value of TRUE, FALSE, or unknown
     */
    private void processConditionalAndExpression()
    {
        processInclusiveOrExpression();

        while (lexer.parseString("&&"))
        {
            processInclusiveOrExpression();
            Node expr = new Node(NodeType.OPERATOR, "&&");
            expr.insertChildNode(stack.pop());
            expr.insertChildNode(stack.pop());
            stack.push(expr);
        }
    }

    private void processInclusiveOrExpression()
    {
        processExclusiveOrExpression();

        while (lexer.parseChar('|', '|'))
        {
            processExclusiveOrExpression();
            Node expr = new Node(NodeType.OPERATOR, "|");
            expr.insertChildNode(stack.pop());
            expr.insertChildNode(stack.pop());
            stack.push(expr);
        }
    }

    private void processExclusiveOrExpression()
    {
        processAndExpression();

        while (lexer.parseChar('^'))
        {
            processAndExpression();
            Node expr = new Node(NodeType.OPERATOR, "^");
            expr.insertChildNode(stack.pop());
            expr.insertChildNode(stack.pop());
            stack.push(expr);
        }
    }

    private void processAndExpression()
    {
        processRelationalExpression();

        while (lexer.parseChar('&', '&'))
        {
            processRelationalExpression();
            Node expr = new Node(NodeType.OPERATOR, "&");
            expr.insertChildNode(stack.pop());
            expr.insertChildNode(stack.pop());
            stack.push(expr);
        }
    }

    /**
     * Parse of an expression that is a relation between expressions.
     */
    private void processRelationalExpression()
    {
        processAdditiveExpression();

        for (;;)
        {
            if (lexer.parseString("=="))
            {
                processAdditiveExpression();
                Node expr = new Node(NodeType.OPERATOR, "==");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else if (lexer.parseString("!="))
            {
                processAdditiveExpression();
                Node expr = new Node(NodeType.OPERATOR, "!=");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else if (lexer.parseString("="))
            {
                if (allowSingleEquals)
                {
                    // Allowed when processing UPDATE clause ("SET xyz = val")
                    processAdditiveExpression();
                    Node expr = new Node(NodeType.OPERATOR, "==");
                    expr.insertChildNode(stack.pop());
                    expr.insertChildNode(stack.pop());
                    stack.push(expr);
                }
                else
                {
                    // Assignment operator is invalid (user probably meant to specify "==")
                    throw new QueryCompilerSyntaxException("Invalid operator \"=\". Did you mean to use \"==\"?");
                }
            }
            else if (lexer.parseString("<="))
            {
                processAdditiveExpression();
                Node expr = new Node(NodeType.OPERATOR, "<=");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else if (lexer.parseString(">="))
            {
                processAdditiveExpression();
                Node expr = new Node(NodeType.OPERATOR, ">=");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else if (lexer.parseChar('<'))
            {
                processAdditiveExpression();
                Node expr = new Node(NodeType.OPERATOR, "<");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else if (lexer.parseChar('>'))
            {
                processAdditiveExpression();
                Node expr = new Node(NodeType.OPERATOR, ">");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else if (lexer.parseString("instanceof"))
            {
                processAdditiveExpression();
                Node expr = new Node(NodeType.OPERATOR, "instanceof");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else
            {
                break;
            }
        }
    }

    protected void processAdditiveExpression()
    {
        processMultiplicativeExpression();

        for (;;)
        {
            if (lexer.parseChar('+'))
            {
                processMultiplicativeExpression();
                Node expr = new Node(NodeType.OPERATOR, "+");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else if (lexer.parseChar('-'))
            {
                processMultiplicativeExpression();
                Node expr = new Node(NodeType.OPERATOR, "-");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else
            {
                break;
            }
        }
    }

    protected void processMultiplicativeExpression()
    {
        processUnaryExpression();

        for (;;)
        {
            if (lexer.parseChar('*'))
            {
                processUnaryExpression();
                Node expr = new Node(NodeType.OPERATOR, "*");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else if (lexer.parseChar('/'))
            {
                processUnaryExpression();
                Node expr = new Node(NodeType.OPERATOR, "/");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else if (lexer.parseChar('%'))
            {
                processUnaryExpression();
                Node expr = new Node(NodeType.OPERATOR, "%");
                expr.insertChildNode(stack.pop());
                expr.insertChildNode(stack.pop());
                stack.push(expr);
            }
            else
            {
                break;
            }
        }
    }

    protected void processUnaryExpression()
    {
        if (lexer.parseString("++"))
        {
            throw new QueryCompilerSyntaxException("Unsupported operator '++'");
        }
        else if (lexer.parseString("--"))
        {
            throw new QueryCompilerSyntaxException("Unsupported operator '--'");
        }

        if (lexer.parseChar('+'))
        {
            // Just swallow + and leave remains on the stack
            processUnaryExpression();
        }
        else if (lexer.parseChar('-'))
        {
            processUnaryExpression();
            Node expr = new Node(NodeType.OPERATOR, "-");
            expr.insertChildNode(stack.pop());
            stack.push(expr);
        }
        else if (lexer.parseChar('~'))
        {
            processUnaryExpression();
            Node expr = new Node(NodeType.OPERATOR, "~");
            expr.insertChildNode(stack.pop());
            stack.push(expr);
        }
        else if (lexer.parseChar('!'))
        {
            processUnaryExpression();
            Node expr = new Node(NodeType.OPERATOR, "!");
            expr.insertChildNode(stack.pop());
            stack.push(expr);
        }
        else
        {
            processPrimary();
        }
    }

    /**
     * Parses the primary. First look for a literal (e.g. "text"), then
     * an identifier(e.g. variable) In the next step, call a function, if
     * executing a function, on the literal or the identifier found.
     */
    protected void processPrimary()
    {
        if (lexer.parseString("DISTINCT ") || lexer.parseString("distinct"))
        {
            // Aggregates can have "count(DISTINCT field1)"
            Node distinctNode = new Node(NodeType.OPERATOR, "DISTINCT");
            processExpression();
            Node identifierNode = stack.pop();
            distinctNode.appendChildNode(identifierNode);
            stack.push(distinctNode);
            return;
        }

        // Find any cast first, and remove from top of stack since we put after the cast component
        Node castNode = null;
        if (processCast())
        {
            castNode = stack.pop();
        }

        if (lexer.peekString("IF(") || lexer.peekString("if(") || lexer.peekString("IF (") || lexer.peekString("if ("))
        {
            processIfElseExpression();
            return;
        }
        if (processCreator())
        {
            // "new MyClass(...)", allow chain of method invocations
            boolean endOfChain = false;
            while (!endOfChain)
            {
                if (lexer.parseChar('.'))
                {
                    if (processMethod())
                    {
                        // Add method invocation as a child node of what it is invoked on
                        Node invokeNode = stack.pop();
                        Node invokedNode = stack.peek();
                        invokedNode.appendChildNode(invokeNode);
                    }
                }
                else
                {
                    endOfChain = true;
                }
            }
            if (castNode != null)
            {
                // TODO Add cast of creator expression
                throw new NucleusException("Dont currently support compile of cast of creator expression");
            }
            return;
        }
        else if (processLiteral())
        {
            // Literal, allow chain of method invocations
            boolean endOfChain = false;
            while (!endOfChain)
            {
                if (lexer.parseChar('.'))
                {
                    if (processMethod())
                    {
                        // Add method invocation as a child node of what it is invoked on
                        Node invokeNode = stack.pop();
                        Node invokedNode = stack.peek();
                        invokedNode.appendChildNode(invokeNode);
                    }
                }
                else
                {
                    endOfChain = true;
                }
            }
            if (castNode != null)
            {
                throw new NucleusException("Dont currently support compile of cast of literal expression");
                // TODO Add cast of literal
            }
            return;
        }
        else if (processMethod())
        {
            // Static method call
            if (castNode != null)
            {
                throw new NucleusException("Dont currently support compile of cast of static method call");
                // TODO Add cast of static method call
            }
            return;
        }
        else if (processArray())
        {
            // Array, allow chain of method invocations
            boolean endOfChain = false;
            while (!endOfChain)
            {
                if (lexer.parseChar('.'))
                {
                    if (processMethod())
                    {
                        // Add method invocation as a child node of what it is invoked on
                        Node invokeNode = stack.pop();
                        Node invokedNode = stack.peek();
                        invokedNode.appendChildNode(invokeNode);
                    }
                }
                else
                {
                    endOfChain = true;
                }
            }
            if (castNode != null)
            {
                throw new NucleusException("Dont currently support compile of cast of array expression");
                // TODO Add cast of array
            }
            return;
        }

        int sizeBeforeBraceProcessing = stack.size();
        boolean braceProcessing = false;
        if (lexer.parseChar('('))
        {
            // "({expr1})"
            processExpression();
            if (!lexer.parseChar(')'))
            {
                throw new QueryCompilerSyntaxException("expected ')'", lexer.getIndex(), lexer.getInput());
            }

            if (!lexer.parseChar('.'))
            {
                // TODO If we have a cast, then apply to the current node (with the brackets)
                return;
            }

            // Has follow on expression "({expr1}).{expr2}" so continue
            braceProcessing = true;
        }

        // We will have an identifier (variable, parameter, or field of candidate class)
        if (processMethod())
        {
        }
        else if (processIdentifier())
        {
        }
        else
        {
            throw new QueryCompilerSyntaxException("Method/Identifier expected", lexer.getIndex(), lexer.getInput());
        }

        // Save the stack size just before this component for use later in squashing all nodes
        // down to a single Node in the stack with all others chained off from it
        int size = stack.size();
        if (braceProcessing)
        {
            size = sizeBeforeBraceProcessing+1;
        }

        while (lexer.parseChar('.'))
        {
            if (processMethod())
            {
                // "a.method(...)"
            }
            else if (processIdentifier())
            {
                // "a.field"
            }
            else
            {
                throw new QueryCompilerSyntaxException("Identifier expected", lexer.getIndex(), lexer.getInput());
            }
        }

        // Generate Node tree, including chained operations
        // e.g identifier.methodX().methodY().methodZ() 
        //     -> node (IDENTIFIER) with child (INVOKE), with child (INVOKE), with child (INVOKE)
        // e.g identifier.fieldX.fieldY.fieldZ
        //     -> node (IDENTIFIER) with child (IDENTIFIER), with child (IDENTIFIER), with child (IDENTIFIER)
        if (castNode != null)
        {
            // Put the CAST as a child of the Node being cast
            stack.peek().appendChildNode(castNode);
        }

        // For all added nodes in this block, step back and chain them. Examples :-
        // a.b.c being compiled to "Node[IDENTIFIER, a] -> Node[IDENTIFIER, b] -> Node[IDENTIFIER, c]"
        // ((B)a).c being compiled to "Node[IDENTIFIER, a] -> Node[CAST, B] -> Node[IDENTIFIER, c]"
        while (stack.size() > size)
        {
            Node top = stack.pop();
            Node peek = stack.peek();
            Node lastDescendant = getLastDescendantNodeForNode(peek);
            if (lastDescendant != null)
            {
                lastDescendant.appendChildNode(top);
            }
            else
            {
                // The peek node has multiple children, so cannot just put the top Node after the last child
                Node primNode = new Node(NodeType.PRIMARY);
                primNode.appendChildNode(peek);
                primNode.appendChildNode(top);

                // Remove "peek" node and replace with primNode
                stack.pop();
                stack.push(primNode);
            }
        }
    }

    /**
     * Method to parse the query syntax for an "IF ... ELSE ..." expression.
     * Creates a Node of type "CASE" with children in the order 
     * <pre>ifNode, ifActionNode [, ifNode, ifActionNode]*, elseActionNode</pre>
     */
    private void processIfElseExpression()
    {
        Node caseNode = new Node(NodeType.CASE);

        // Process "IF (expr) actionExpr"
        if (!lexer.parseString("IF") || lexer.parseString("if"))
        {
            throw new QueryCompilerSyntaxException("Expected IF or if", lexer.getIndex(), lexer.getInput());
        }

        if (!lexer.parseChar('('))
        {
            throw new QueryCompilerSyntaxException("Expected '(' as part of IF (...)", lexer.getIndex(), lexer.getInput());
        }
        processExpression();
        Node whenNode = stack.pop();
        caseNode.appendChildNode(whenNode);
        if (!lexer.parseChar(')'))
        {
            throw new QueryCompilerSyntaxException("Expected ')' as part of IF (...)", lexer.getIndex(), lexer.getInput());
        }

        processExpression();
        Node actionNode = stack.pop();
        caseNode.appendChildNode(actionNode);

        // Process "ELSE IF (expr) actionExpr ELSE actionExpr"
        // TODO Detect lack of ELSE with no IF and throw exception
        boolean elseClause = false;
        while (lexer.parseString("ELSE") || lexer.parseString("else"))
        {
            boolean hasIf = false;
            if (lexer.parseString("IF") || lexer.parseString("if"))
            {
                hasIf = true;
                if (!lexer.parseChar('('))
                {
                    throw new QueryCompilerSyntaxException("Expected '(' as part of IF (...)", lexer.getIndex(), lexer.getInput());
                }
                processExpression();
                whenNode = stack.pop();
                caseNode.appendChildNode(whenNode);
                if (!lexer.parseChar(')'))
                {
                    throw new QueryCompilerSyntaxException("Expected ')' as part of IF (...)", lexer.getIndex(), lexer.getInput());
                }
            }

            processExpression();
            actionNode = stack.pop();
            caseNode.appendChildNode(actionNode);
            if (!hasIf)
            {
                elseClause = true;
            }
        }

        if (!elseClause)
        {
            throw new QueryCompilerSyntaxException("Use of IF {expr} ELSE IF {expr} structure should always terminate with ELSE {expr} but doesn't", lexer.getIndex(), lexer.getInput());
        }
        stack.push(caseNode);
    }

    /**
     * Parse any cast expression.
     * If a cast is found will create a Node of type CAST with the value being the class to cast to.
     * @return Whether a cast was processed
     */
    private boolean processCast()
    {
        String typeName = lexer.parseCast();
        if (typeName == null)
        {
            return false;
        }

        Node castNode = new Node(NodeType.CAST, typeName);
        stack.push(castNode);
        return true;
    }

    /**
     * Method to parse "new a.b.c(param1[,param2], ...)" and create a Node of type CREATOR.
     * The Node at the top of the stack after this call will have any arguments defined in its "properties".
     * @return whether method syntax was found.
     */
    private boolean processCreator()
    {
        if (lexer.parseString("new "))
        {
            int size = stack.size();
            if (!processMethod())
            {
                if (!processIdentifier())
                {
                    throw new QueryCompilerSyntaxException("Identifier expected", lexer.getIndex(), lexer.getInput());
                }

                while (lexer.parseChar('.'))
                {
                    if (processMethod())
                    {
                        // "a.method(...)"
                    }
                    else if (processIdentifier())
                    {
                        // "a.field"
                    }
                    else
                    {
                        throw new QueryCompilerSyntaxException("Identifier expected", lexer.getIndex(), lexer.getInput());
                    }
                }
            }
            while (stack.size() - 1 > size)
            {
                Node top = stack.pop();
                Node peek = stack.peek();
                peek.insertChildNode(top);
            }
            Node expr = stack.pop();
            Node newExpr = new Node(NodeType.CREATOR);
            newExpr.insertChildNode(expr);
            stack.push(newExpr);
            return true;
        }
        return false;
    }

    /**
     * Method to parse "methodName(param1[,param2], ...)" and create a Node of type INVOKE.
     * The Node at the top of the stack after this call will have any arguments defined in its "properties".
     * @return whether method syntax was found.
     */
    private boolean processMethod()
    {
        String method = lexer.parseMethod();
        if (method != null)
        {
            lexer.skipWS();
            lexer.parseChar('(');

            if (strict)
            {
                // Enable strictness checking for levels of JDOQL
                // Note that this only checks the method name and not the arguments/types
                if (Arrays.binarySearch(jdoqlMethodNames, method) < 0)
                {
                    throw new QueryCompilerSyntaxException("Query uses method \"" + method + 
                        "\" but this is not a standard JDOQL method name");
                }
            }

            // Found syntax for a method, so invoke the method
            Node expr = new Node(NodeType.INVOKE, method);
            if (!lexer.parseChar(')'))
            {
                do
                {
                    // Argument for the method call, add as a node property
                    processExpression();
                    expr.addProperty(stack.pop());
                }
                while (lexer.parseChar(','));

                if (!lexer.parseChar(')'))
                {
                    throw new QueryCompilerSyntaxException("')' expected", lexer.getIndex(), lexer.getInput());
                }
            }

            stack.push(expr);
            return true;
        }
        return false;
    }

    /**
     * Method to parse an array expression ("{a,b,c}"), creating an ARRAY node with the node value
     * being a List<Node> of the elements. Also handles processing of subsequent "length" 'method'
     * which is special case for arrays and not caught by processMethod() since it doesn't have "()".
     * @return Whether an array was parsed from the current position
     */
    private boolean processArray()
    {
        if (lexer.parseChar('{'))
        {
            // Array
            List<Node> elements = new ArrayList();
            while (!lexer.parseChar('}'))
            {
                processPrimary();
                Node elementNode = stack.pop();
                elements.add(elementNode);

                if (lexer.parseChar('}'))
                {
                    break;
                }
                else if (!lexer.parseChar(','))
                {
                    throw new QueryCompilerSyntaxException("',' or '}' expected", lexer.getIndex(), lexer.getInput());
                }
            }

            Node arrayNode = new Node(NodeType.ARRAY, elements);
            stack.push(arrayNode);

            // Check for "length" since won't be picked up by processMethod
            if (lexer.parseString(".length"))
            {
                Node lengthMethod = new Node(NodeType.INVOKE, "length");
                arrayNode.appendChildNode(lengthMethod);
            }

            return true;
        }
        return false;
    }

    /**
     * A literal is one value of any type.
     * Supported literals are of types String, Floating Point, Integer,
     * Character, Boolean and null e.g. 'J', "String", 1, 1.8, true, false, null.
     * @return The parsed literal
     */
    protected boolean processLiteral()
    {
        Object litValue = null;

        String sLiteral;
        BigDecimal fLiteral;
        BigInteger iLiteral;
        Boolean bLiteral;

        boolean single_quote_next = lexer.nextIsSingleQuote();
        if ((sLiteral = lexer.parseStringLiteral()) != null)
        {
            // Both String and Character are allowed to use single-quotes
            // so we need to check if it was single-quoted and
            // use CharacterLiteral if length is 1.
            if (sLiteral.length() == 1 && single_quote_next)
            {
                litValue = Character.valueOf(sLiteral.charAt(0));
            }
            else
            {
                litValue = sLiteral;
            }
        }
        else if ((fLiteral = lexer.parseFloatingPointLiteral()) != null)
        {
            litValue = fLiteral;
        }
        else if ((iLiteral = lexer.parseIntegerLiteral()) != null)
        {
            // Represent as BigInteger or Long depending on length
            String longStr = "" + iLiteral.longValue();
            if (longStr.length() < iLiteral.toString().length())
            {
                litValue = iLiteral;
            }
            else
            {
                litValue = iLiteral.longValue();
            }
        }
        else if ((bLiteral = lexer.parseBooleanLiteral()) != null)
        {
            litValue = bLiteral;
        }
        else if (lexer.parseNullLiteral())
        {
        }
        else
        {
            return false;
        }

        stack.push(new Node(NodeType.LITERAL, litValue));
        return true;
    }

    /**
     * An identifier always designates a reference to a single value.
     * A single value can be one collection, one field.
     * @return The parsed identifier
     */
    private boolean processIdentifier()
    {
        String id = lexer.parseIdentifier();
        if (id == null)
        {
            return false;
        }
        char first = id.charAt(0);
        if (first == ':')
        {
            if (explicitParams)
            {
                throw new QueryCompilerSyntaxException("Explicit parameters defined for query, yet implicit parameter syntax (\"" + id + "\") found");
            }

            // Named parameter - stored as String
            String name = id.substring(1);
            Node expr = new ParameterNode(NodeType.PARAMETER, name, getPositionFromParameterName(name));
            stack.push(expr);
            return true;
        }

        Node expr = new Node(NodeType.IDENTIFIER, id);
        stack.push(expr);
        return true;
    }

    private List<Object> parameterNameList = null;

    private int getPositionFromParameterName(Object name)
    {
        if (parameterNameList == null)
        {
            parameterNameList = new ArrayList<Object>(1);
        }

        int pos = parameterNameList.indexOf(name);
        if (pos == -1)
        {
            pos = parameterNameList.size();
            parameterNameList.add(name);
        }

        return pos;
    }
}
