package togos.schemaschemademo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Reader;

import togos.asyncstream.BaseStreamSource;
import togos.asyncstream.StreamDestination;
import togos.asyncstream.StreamUtil;
import togos.codeemitter.WordUtil;
import togos.codeemitter.sql.SQLEmitter;
import togos.codeemitter.structure.rdb.TableDefinition;
import togos.function.Function;
import togos.schemaschema.ComplexType;
import togos.schemaschema.Predicates;
import togos.schemaschema.SchemaObject;
import togos.schemaschema.Types;
import togos.schemaschema.parser.CommandInterpreters;
import togos.schemaschema.parser.SchemaInterpreter;

public class SchemaProcessor
{
	protected static Function<String,String> PASCAL_CASIFIER = new Function<String,String>() {
		@Override public String apply(String phrase) throws RuntimeException {
			return WordUtil.toPascalCase(phrase);
		}
	};
	
	static class RelationalClassFilter extends BaseStreamSource<ComplexType> implements StreamDestination<SchemaObject>
	{
		@Override
		public void data(SchemaObject value) throws Exception {
			if( value instanceof ComplexType && SSDPredicates.isRelationalClass(value) ) {
				_data((ComplexType)value);
			}
		}
	}
	
	public static void main( String[] args ) throws Exception {
		String sourceFilename = "-";
		for( int i=0; i<args.length; ++i ) {
			if( !args[i].startsWith("-") ) {
				sourceFilename = args[i];
			}
		}
		
		Reader sourceReader = "-".equals(sourceFilename) ?
			new InputStreamReader(System.in) :
			new FileReader(sourceFilename);
		
		SchemaInterpreter sp = new SchemaInterpreter();
			
		sp.defineClassPredicate( Predicates.IS_SELF_KEYED );
		sp.defineClassPredicate( Predicates.EXTENDS );
		SSDPredicates.defineOn(sp);
		sp.defineFieldModifier("key", SchemaInterpreter.FieldIndexModifierSpec.INSTANCE );
		sp.defineFieldModifier("index", SchemaInterpreter.FieldIndexModifierSpec.INSTANCE );
		sp.defineFieldPredicate( Predicates.IS_NULLABLE );
		
		sp.defineType( Types.CLASS );
		
		CommandInterpreters.defineTypeDefinitionCommands(sp);
		
		RelationalClassFilter relationalClassFilter = new RelationalClassFilter();
		
		{
			final FileWriter sqlWriter = new FileWriter(new File("create-tables.sql"));
			final SQLEmitter sqlEmitter = new SQLEmitter();
			final TableCreationSQLGenerator tcsg = new TableCreationSQLGenerator(sqlEmitter, PASCAL_CASIFIER, PASCAL_CASIFIER);
			tcsg.pipe(new StreamDestination<TableDefinition>() {
				@Override public void data(TableDefinition td) throws Exception {
					sqlEmitter.emitTableCreation(td);
				};
				@Override public void end() throws Exception {
					sqlWriter.close();
				}
			});
			StreamUtil.pipe(sqlEmitter, sqlWriter, false);
			relationalClassFilter.pipe(tcsg);
		}
		sp.pipe( relationalClassFilter );
		
		sp.parse( sourceReader, sourceFilename );
	}
}
