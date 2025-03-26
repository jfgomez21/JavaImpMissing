package jim.javaparser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import jim.models.FileTypeEntry;

//TODO - rename ParseActionVisitor
//TODO - catch block
//TODO - finally block
public class ClassOrInterfaceTypeVisitor extends VoidVisitorAdapter<Map<String, FileTypeEntry>> {
	private final Pattern classNamePattern = Pattern.compile("[A-Z]+[A-Za-z0-9_$]*");

	private void setRange(FileTypeEntry entry, Node node){
		Optional<Range> range = node.getRange();

		if(range.isPresent()){
			Range r = range.get();

			entry.position.line = r.begin.line;
			entry.position.column = r.begin.column;
		}
	}

	private void addType(Map<String, FileTypeEntry> entries, ClassOrInterfaceType type){
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

	private void addTypeArguments(Map<String, FileTypeEntry> entries, ClassOrInterfaceType type){
		Optional<NodeList<Type>> opt = type.getTypeArguments();

		if(opt.isPresent()){
			for(Type t : opt.get()){
				if(t.isClassOrInterfaceType()){
					visit((ClassOrInterfaceType) t, entries);
				}
			}
		}
	}

	@Override
	public void visit(ClassOrInterfaceType type, Map<String, FileTypeEntry> entries){
		addType(entries, type);	
		addTypeArguments(entries, type);
	}
	
	@Override
	public void visit(TypeParameter parameter, Map<String, FileTypeEntry> entries){
		entries.remove(parameter.getName().asString());
	}

	private boolean isClassName(SimpleName name){
		return classNamePattern.matcher(name.asString()).matches();
	}

	private void processFieldAccessExpr(Map<String, FileTypeEntry> entries, FieldAccessExpr field){
		Expression exp = field;

		while(!exp.isNameExpr()){
			if(exp.isFieldAccessExpr()){
				exp = exp.asFieldAccessExpr().getScope();
			}
		}
		
		NameExpr name = exp.asNameExpr();
		SimpleName nm = name.getName();

		if(isClassName(nm)){
			ClassOrInterfaceType type = new ClassOrInterfaceType();
			type.setName(nm);
			type.setRange(name.getRange().get());

			visit(type, entries);
		}
	}

	private void processClassExpr(Map<String, FileTypeEntry> entries, ClassExpr expr){
		Type type = expr.getType();

		if(type.isClassOrInterfaceType()){
			visit(type.asClassOrInterfaceType(), entries);
		}
	}

	private void processExpression(Expression exp, Map<String, FileTypeEntry> entries){
		if(exp.isNameExpr()){
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
			processFieldAccessExpr(entries, exp.asFieldAccessExpr());
		}
		else if(exp.isMethodCallExpr()){
			visit(exp.asMethodCallExpr(), entries);
		}
		else if(exp.isObjectCreationExpr()){
			visit(exp.asObjectCreationExpr(), entries);
		}
		else if(exp.isClassExpr()){
			processClassExpr(entries, exp.asClassExpr());
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

	@Override
	public void visit(TryStmt statement, Map<String, FileTypeEntry> entries){
		for(Expression ex : statement.getResources()){
			if(ex.isVariableDeclarationExpr()){
				for(VariableDeclarator declarator : ex.asVariableDeclarationExpr().getVariables()){
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
		}	

		for(Statement st : statement.getTryBlock().getStatements()){
			if(st.isExpressionStmt()){
				processExpression(st.asExpressionStmt().getExpression(), entries);
			}
			else if(st.isReturnStmt()){
				Optional<Expression> opt = st.asReturnStmt().getExpression();

				if(opt.isPresent()){
					processExpression(opt.get(), entries);
				}
			}
		}
	} 
}
