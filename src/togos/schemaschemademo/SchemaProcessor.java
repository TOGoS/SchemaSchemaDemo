package togos.schemaschemademo;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;

import togos.asyncstream.BaseStreamSource;
import togos.asyncstream.StreamDestination;
import togos.codeemitter.WordUtil;
import togos.codeemitter.sql.SQLEmitter;
import togos.codeemitter.structure.rdb.TableDefinition;
import togos.function.Function;
import togos.lang.BaseSourceLocation;
import togos.lang.CompileError;
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
	
	static class RelationalClassFilter<E extends Throwable> extends BaseStreamSource<ComplexType, E> implements StreamDestination<SchemaObject, E>
	{
		@Override
		public void data(SchemaObject value) throws E {
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
		
		RelationalClassFilter<CompileError> relationalClassFilter = new RelationalClassFilter<CompileError>();
		
		{
			final ArrayList<String> tableList = new ArrayList<String>();
			final FileWriter createTablesWriter = new FileWriter(new File("create-tables.sql"));
			final SQLEmitter createTablesSqlEmitter = new SQLEmitter(createTablesWriter);
			
			final FileWriter dropTablesWriter = new FileWriter(new File("drop-tables.sql"));
			final SQLEmitter dropTablesSqlEmitter = new SQLEmitter(dropTablesWriter);
			final TableCreationSQLGenerator tcsg = new TableCreationSQLGenerator(createTablesSqlEmitter, PASCAL_CASIFIER, PASCAL_CASIFIER);
			tcsg.pipe(new StreamDestination<TableDefinition, CompileError>() {
				@Override public void data(TableDefinition td) throws CompileError {
					try {
						createTablesSqlEmitter.emitTableCreation(td);
						tableList.add(td.name);
					} catch( CompileError e ) {
						throw e;
					} catch( Exception e ) {
						throw new CompileError(e, BaseSourceLocation.NONE);
					}
				};
				@Override public void end() throws CompileError {
					try {
						createTablesWriter.close();
						
						Collections.reverse(tableList);
						for( String tableName : tableList ) {
							dropTablesSqlEmitter.emitDropTable(tableName);
						}
						dropTablesWriter.close();
					} catch( CompileError e ) {
						throw e;
					} catch( Exception e ) {
						throw new CompileError(e, BaseSourceLocation.NONE);
					}
				}
			});
			relationalClassFilter.pipe(tcsg);
		}
		sp.pipe( relationalClassFilter );
		
		sp.parse( sourceReader, sourceFilename );
	}
}
