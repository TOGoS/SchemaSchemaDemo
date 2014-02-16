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
import togos.schemaschema.PropertyUtil;
import togos.schemaschema.SchemaObject;
import togos.schemaschema.namespaces.Application;
import togos.schemaschema.namespaces.Core;
import togos.schemaschema.namespaces.DataTypeTranslation;
import togos.schemaschema.namespaces.Types;
import togos.schemaschema.parser.CommandInterpreters;
import togos.schemaschema.parser.SchemaInterpreter;

public class SchemaProcessor
{
	protected static Function<String,String> PASCAL_CASIFIER = new Function<String,String>() {
		@Override public String apply(String phrase) throws RuntimeException {
			return WordUtil.toPascalCase(phrase);
		}
	};
	
	static class TableClassFilter<E extends Throwable> extends BaseStreamSource<ComplexType, E> implements StreamDestination<SchemaObject, E>
	{
		@Override
		public void data(SchemaObject value) throws E {
			if( value instanceof ComplexType && PropertyUtil.getFirstInheritedBoolean(value, Application.HAS_DB_TABLE, false) ) {
				_data((ComplexType)value);
			}
		}
	}
	
	static String USAGE_TEXT =
		"Usage: SchemaProcessor [options] [schema file]\n" +
		"Options:\n" +
		"  -? or -h ; output help text and exit";
	
	public static void main( String[] args ) throws Exception {
		String sourceFilename = "-";
		for( int i=0; i<args.length; ++i ) {
			if( "-?".equals(args[i]) || "-h".equals(args[i]) || "--help".equals(args[i]) ) {
				System.out.println(USAGE_TEXT);
				System.exit(0);
			} else if( !args[i].startsWith("-") ) {
				sourceFilename = args[i];
			} else {
				System.err.println("Unrecognized argument: "+args[i]);
				System.err.println(USAGE_TEXT);
				System.exit(1);
			}
		}
		
		Reader sourceReader = "-".equals(sourceFilename) ?
			new InputStreamReader(System.in) :
			new FileReader(sourceFilename);
		
		SchemaInterpreter sp = new SchemaInterpreter();
		sp.defineImportable( Core.NS );
		sp.defineImportable( Application.NS );
		sp.defineImportable( DataTypeTranslation.NS );
		sp.defineImportable( Types.NS );
		sp.defineImportable( Core.RDF_NS );
		sp.defineImportable( Core.RDFS_NS );
		
		sp.defineFieldModifier("key", SchemaInterpreter.FieldIndexModifierSpec.INSTANCE );
		sp.defineFieldModifier("index", SchemaInterpreter.FieldIndexModifierSpec.INSTANCE );
		
		CommandInterpreters.defineTypeDefinitionCommands(sp);
		CommandInterpreters.defineImportCommand(sp);
		
		TableClassFilter<CompileError> tableClassFilter = new TableClassFilter<CompileError>();
		
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
			tableClassFilter.pipe(tcsg);
		}
		
		{
			final FileWriter phpSchemaWriter = new FileWriter(new File("schema.php"));
			final PHPSchemaDumper psd = new PHPSchemaDumper(phpSchemaWriter);
			tableClassFilter.pipe(new StreamDestination<ComplexType, CompileError>() {
				@Override public void data(ComplexType value) throws CompileError {
					try {
						psd.data(value);
					} catch( CompileError e ) {
						throw e;
					} catch( Exception e ) {
						throw new CompileError(e, BaseSourceLocation.NONE);
					}
				};
				@Override public void end() throws CompileError {
					try {
						psd.end();
						phpSchemaWriter.close();
					} catch( CompileError e ) {
						throw e;
					} catch( Exception e ) {
						throw new CompileError(e, BaseSourceLocation.NONE);
					}
				}
			});
		}
		
		sp.pipe( tableClassFilter );
		
		sp.parse( sourceReader, sourceFilename );
	}
}
