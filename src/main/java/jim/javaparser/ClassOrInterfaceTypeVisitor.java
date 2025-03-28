package jim.javaparser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import jim.models.FileTypeEntry;

//TODO - rename ParseActionVisitor
public class ClassOrInterfaceTypeVisitor extends VoidVisitorAdapter<Map<String, FileTypeEntry>> {
	private final Pattern classNamePattern = Pattern.compile("[A-Z]+[A-Za-z0-9_$]*");

	private void println(Object obj){
		System.out.println(String.format("%s - %s", obj.getClass(), obj));
	}

	private void setRange(FileTypeEntry entry, Node node){
		Optional<Range> range = node.getRange();

		if(range.isPresent()){
			Range r = range.get();

			entry.position.line = r.begin.line;
			entry.position.column = r.begin.column;
		}
	}

	private void addType(ClassOrInterfaceType type, Map<String, FileTypeEntry> entries){
		Deque<String> deque = new ArrayDeque<>();
		deque.add(type.getName().asString());

		Optional<ClassOrInterfaceType> opt = type.getScope();
		ClassOrInterfaceType parent = type;
		
		while(opt.isPresent()){
			parent = opt.get();

			deque.add(parent.getName().asString());

			opt = parent.getScope();
		}

		StringBuilder str = new StringBuilder();

		while(!deque.isEmpty()){
			str.append(deque.pollLast());
			str.append(".");
		}

		str.setLength(Math.max(0, str.length() - 1));

		FileTypeEntry entry = new FileTypeEntry();
		entry.value = str.toString();

		setRange(entry, parent);

		entries.put(entry.value, entry);
	}

	private void processType(Type type, Map<String, FileTypeEntry> entries){
		if(type.isUnionType()){
			for(ReferenceType t : type.asUnionType().getElements()){
				processType(t, entries);
			}
		}
		else if(type.isClassOrInterfaceType()){
			visit(type.asClassOrInterfaceType(), entries);
		}
	}

	private void addTypeArguments(ClassOrInterfaceType type, Map<String, FileTypeEntry> entries){
		Optional<NodeList<Type>> opt = type.getTypeArguments();

		if(opt.isPresent()){
			for(Type t : opt.get()){
				processType(t, entries);
			}
		}
	}

	@Override
	public void visit(ClassOrInterfaceType type, Map<String, FileTypeEntry> entries){
		addType(type, entries);	
		addTypeArguments(type, entries);
	}
	
	@Override
	public void visit(TypeParameter parameter, Map<String, FileTypeEntry> entries){
		entries.remove(parameter.getName().asString());
	}

	private boolean isClassName(SimpleName name){
		return classNamePattern.matcher(name.asString()).matches();
	}

	private void processClassExpr(ClassExpr expr, Map<String, FileTypeEntry> entries){
		processType(expr.getType(), entries);
	}

	private void processArrayInitializerExpr(ArrayInitializerExpr expr, Map<String, FileTypeEntry> entries){
		for(Expression exp : expr.getValues()){
			processExpression(exp, entries);
		}
	}

	private void processExpression(Expression exp, Map<String, FileTypeEntry> entries){
		if(exp.isVariableDeclarationExpr()){
			for(VariableDeclarator declarator : exp.asVariableDeclarationExpr().getVariables()){
				Type type = declarator.getType();

				if(type.isClassOrInterfaceType()){
					visit(type.asClassOrInterfaceType(), entries);
				}

				Optional<Expression> opt = declarator.getInitializer();

				if(opt.isPresent()){
					processExpression(opt.get(), entries);	
				}
			}
		}
		else if(exp.isNameExpr()){
			NameExpr nm = exp.asNameExpr();
			SimpleName name = nm.getName();

			if(isClassName(name)){
				ClassOrInterfaceType type = new ClassOrInterfaceType();
				type.setName(name);
				type.setRange(nm.getRange().get());

				visit(type, entries);
			}
		}
		else if(exp.isFieldAccessExpr()){
			processExpression(exp.asFieldAccessExpr().getScope(), entries);
		}
		else if(exp.isMethodCallExpr()){
			visit(exp.asMethodCallExpr(), entries);
		}
		else if(exp.isObjectCreationExpr()){
			visit(exp.asObjectCreationExpr(), entries);
		}
		else if(exp.isClassExpr()){
			processClassExpr(exp.asClassExpr(), entries);
		}
		else if(exp.isArrayInitializerExpr()){
			processArrayInitializerExpr(exp.asArrayInitializerExpr(), entries);	
		}
		else if(exp.isLambdaExpr()){
			visit(exp.asLambdaExpr(), entries);
		}
		else if(exp.isMarkerAnnotationExpr()){
			visit(exp.asMarkerAnnotationExpr(), entries);
		}
		else if(exp.isNormalAnnotationExpr()){
			visit(exp.asNormalAnnotationExpr(), entries);
		}
		else if(exp.isSingleMemberAnnotationExpr()){
			visit(exp.asSingleMemberAnnotationExpr(), entries);
		}
		else if(exp.isCastExpr()){
			visit(exp.asCastExpr(), entries);
		}
	}

	@Override
	public void visit(ObjectCreationExpr expression, Map<String, FileTypeEntry> entries){
		visit(expression.getType(), entries);

		for(Expression exp : expression.getArguments()){
			processExpression(exp, entries);
		}
	}	

	@Override
	public void visit(MethodCallExpr expression, Map<String, FileTypeEntry> entries){
		for(Expression ex : expression.getArguments()){
			processExpression(ex, entries);	
		}

		Optional<Expression> opt = expression.getScope();

		if(opt.isPresent()){
			processExpression(opt.get(), entries);
		}
	}

	private void processAnnotationExpression(Map<String, FileTypeEntry> entries, Name name, Range range){
		Optional<Name> opt = name.getQualifier();

		if(opt.isEmpty()){
			ClassOrInterfaceType type = new ClassOrInterfaceType();
			type.setName(new SimpleName(name.getIdentifier()));
			type.setRange(range);

			visit(type, entries); 
		}
	}	

	@Override
	public void visit(NormalAnnotationExpr expression, Map<String, FileTypeEntry> entries){
		processAnnotationExpression(entries, expression.getName(), expression.getRange().get());

		for(MemberValuePair pair : expression.getPairs()){
			processExpression(pair.getValue(), entries);
		}
	}	

	@Override
	public void visit(SingleMemberAnnotationExpr expression, Map<String, FileTypeEntry> entries){
		processAnnotationExpression(entries, expression.getName(), expression.getRange().get());
		processExpression(expression.getMemberValue(), entries);
	}	

	@Override
	public void visit(MarkerAnnotationExpr expression, Map<String, FileTypeEntry> entries){
		processAnnotationExpression(entries, expression.getName(), expression.getRange().get());
	}

	private void processStatement(Statement st, Map<String, FileTypeEntry> entries){
		if(st.isExpressionStmt()){
			processExpression(st.asExpressionStmt().getExpression(), entries);
		}
		else if(st.isBlockStmt()){
			visit(st.asBlockStmt(), entries);
		}
		else if(st.isReturnStmt()){
			Optional<Expression> opt = st.asReturnStmt().getExpression();

			if(opt.isPresent()){
				processExpression(opt.get(), entries);
			}
		}
		else if(st.isTryStmt()){
			visit(st.asTryStmt(), entries);
		}
		else if(st.isForEachStmt()){
			visit(st.asForEachStmt(), entries);
		}
	}

	@Override
	public void visit(BlockStmt block, Map<String, FileTypeEntry> entries){
		for(Statement st : block.getStatements()){
			processStatement(st, entries);
		}
	}

	@Override
	public void visit(TryStmt statement, Map<String, FileTypeEntry> entries){
		for(Expression ex : statement.getResources()){
			processExpression(ex, entries);	
		}	

		for(Statement st : statement.getTryBlock().getStatements()){
			processStatement(st, entries);	
		}

		for(CatchClause clause : statement.getCatchClauses()){
			processType(clause.getParameter().getType(), entries);
		}

		Optional<BlockStmt> opt = statement.getFinallyBlock();

		if(opt.isPresent()){
			visit(opt.get(), entries);
		}
	} 

	@Override
	public void visit(ForEachStmt statement, Map<String, FileTypeEntry> entries){
		processExpression(statement.getVariable(), entries);
		processExpression(statement.getIterable(), entries);
		processStatement(statement.getBody(), entries);		
	}

	@Override
	public void visit(LambdaExpr expression, Map<String, FileTypeEntry> entries){
		for(Parameter p : expression.getParameters()){
			processType(p.getType(), entries);	

			for(AnnotationExpr exp : p.getAnnotations()){
				processExpression(exp, entries);
			}
		}

		Optional<Expression> opt = expression.getExpressionBody();

		if(opt.isPresent()){
			processExpression(opt.get(), entries);
		}
		
		processStatement(expression.getBody(), entries);	
	}

	@Override
	public void visit(CastExpr expression, Map<String, FileTypeEntry> entries){
		processType(expression.getType(), entries);
		processExpression(expression.getExpression(), entries);
	}
}
