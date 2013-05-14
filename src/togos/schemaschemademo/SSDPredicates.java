package togos.schemaschemademo;

import static togos.schemaschema.PropertyUtil.getType;
import static togos.schemaschema.PropertyUtil.isTrue;
import togos.lang.BaseSourceLocation;
import togos.schemaschema.BaseSchemaObject;
import togos.schemaschema.Predicate;
import togos.schemaschema.PropertyUtil;
import togos.schemaschema.SchemaObject;
import togos.schemaschema.Type;
import togos.schemaschema.Types;
import togos.schemaschema.parser.SchemaInterpreter;

public class SSDPredicates
{
	private static final BaseSourceLocation SLOC = new BaseSourceLocation(SSDPredicates.class.getName(), 0, 0);
	
	private SSDPredicates() { }
	
	protected static Predicate def( String name, Type objectType ) {
		return new Predicate( name, null, objectType, SLOC );
	}
	
	protected static Predicate def( String name, Type objectType, String comment ) {
		Predicate pred = def( name, objectType );
		PropertyUtil.add(pred.getProperties(), COMMENT, BaseSchemaObject.forScalar(comment, SLOC));
		return pred;
	}
	
	public static final Predicate COMMENT   = new Predicate("comment", null, Types.STRING, SLOC);
	public static final Predicate MYSQL_TYPE= def("MySQL type", Types.STRING, "Name of MySQL type used to represent value in database");
	public static final Predicate SQL_TYPE  = def("SQL type", Types.STRING, "Name of ANSI SQL type used to represent value in database");
	public static final Predicate PHP_TYPE  = def("PHP type", Types.STRING, "Name of PHP type used to represent value in PHP");
	public static final Predicate JSON_TYPE = def("JSON type", Types.STRING, "Name of JS type used to represent value in JSON");
	// TODO: Rename to 'instances match regex'
	public static final Predicate REGEX = def("regex", Types.STRING, "Regular expression to validates values of string-based types");
	public static final Predicate IS_RELATIONAL_CLASS = def("is relational class", Types.BOOLEAN, null);
	
	public static final boolean isRelationalClass( SchemaObject obj ) {
		return isTrue( getType(obj), IS_RELATIONAL_CLASS );
	}
	
	public static void defineOn(SchemaInterpreter sp) throws Exception {
		sp.defineGenericPredicate( COMMENT );
		sp.defineClassPredicate( MYSQL_TYPE );
		sp.defineClassPredicate( SQL_TYPE );
		sp.defineClassPredicate( PHP_TYPE );
		sp.defineClassPredicate( JSON_TYPE );
		sp.defineClassPredicate( REGEX );
		sp.defineClassPredicate( IS_RELATIONAL_CLASS );
	}
}
