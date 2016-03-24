package togos.schemaschemademo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import togos.asyncstream.BaseStreamSource;
import togos.asyncstream.StreamDestination;
import togos.asyncstream.StreamUtil;
import togos.codeemitter.FileUtil;
import togos.codeemitter.sql.SQLEmitter;
import togos.codeemitter.structure.rdb.TableDefinition;
import togos.function.Function;
import togos.lang.BaseSourceLocation;
import togos.lang.CompileError;
import togos.lang.ScriptError;
import togos.lang.SourceLocation;
import togos.schemaschema.ComplexType;
import togos.schemaschema.Namespace;
import togos.schemaschema.PropertyUtil;
import togos.schemaschema.SchemaObject;
import togos.schemaschema.namespaces.Application;
import togos.schemaschema.namespaces.Core;
import togos.schemaschema.namespaces.DataTypeTranslation;
import togos.schemaschema.namespaces.RDB;
import togos.schemaschema.namespaces.Types;
import togos.schemaschema.parser.CommandInterpreters;
import togos.schemaschema.parser.Macros;
import togos.schemaschema.parser.Parser;
import togos.schemaschema.parser.SchemaInterpreter;
import togos.schemaschema.parser.Tokenizer;

public class SchemaProcessor
{
	protected static Function<String,String> POSTGRES_CASIFIER = new Function<String,String>() {
		@Override public String apply(String phrase) throws RuntimeException {
			return phrase.replaceAll("_","").replaceAll("::","_").toLowerCase().replaceAll("[^a-z0-9_]","");
		}
	};
	
	static class ClassClassFilter<C, E extends Throwable> extends BaseStreamSource<C, E> implements StreamDestination<SchemaObject, E>
	{
		final Class<C> c;
		public ClassClassFilter( Class<C> c ) {
			this.c = c;
		}
		
		@Override public void data(SchemaObject value) throws E {
			if( c.isInstance(value) ) {
				_data(c.cast(value));
			}
		}
	}
	
	static class TableClassFilter<E extends Throwable> extends BaseStreamSource<ComplexType, E> implements StreamDestination<SchemaObject, E>
	{
		@Override
		public void data(SchemaObject value) throws E {
			if( value instanceof ComplexType && PropertyUtil.getFirstInheritedBoolean(value, Application.HAS_DB_TABLE, false) ) {
				_data((ComplexType)value);
			}
		}
	}
	
	protected static void dumpNamespaces(Namespace ns, ArrayList<String> summaryLines) {
		for( Object obj : ns.items.values() ) {
			if( obj instanceof SchemaObject ) {
				SchemaObject sobj = (SchemaObject)obj;
				if( sobj.getLongName() != null ) {
					String commentString = "";
					for( SchemaObject co : PropertyUtil.getAllInheritedValues(sobj, Core.COMMENT) ) {
						if( commentString == "" ) commentString = " ; ";
						commentString += co.getScalarValue();
					}
					for( SchemaObject prop : PropertyUtil.getAllInheritedValues(sobj, Core.VALUE_TYPE) ) {
						String n = prop.getLongName();
						if( n == null ) n = prop.getName();
						if( n != null ) commentString += " ; value should be a "+prop.getLongName();
					}
					
					summaryLines.add(sobj.getLongName()+commentString);
				}
			}
		}
		
		for( Namespace contained : ns.containedNamespaces.values() ) {
			dumpNamespaces(contained);
		}
	}
	
	protected static void dumpNamespaces(Namespace ns) {
		ArrayList<String> summaryLines = new ArrayList<String>();
		dumpNamespaces(ns, summaryLines);
		Collections.sort(summaryLines);
		for( String l : summaryLines ) System.out.println(l);
	}
	
	static String USAGE_TEXT =
		"Usage: SchemaProcessor [options] [schema file]\n" +
		"Options:\n" +
		"  -o-schema-php <file.php>\n" +
		"  -o-schema-rdf <file.rdf>\n" +
		"  -php-schema-class-namespace <namespace>\n" +
		"  -o-db-scripts <dir>\n" +
		"  -o-create-tables-script <file>\n" +
		"  -o-drop-tables-script <file>\n" +
		"  -dump-predefs ; print predefined notions and exit\n" +
		"  -? or -h ; output help text and exit";
	
	public static void _main( String[] args ) throws Exception {
		ArrayList<String> sourceFilenames = new ArrayList<String>(); 
		String phpSchemaClassNamespace = "TOGoS_Schema";
		File outputSchemaPhpFile = null;
		// TODO: actually do something with this
		File outputSchemaRdfFile = null;
		File outputCreateTablesScriptFile = null;
		File outputDropTablesScriptFile = null;
		Function<String,String> tableNamer = POSTGRES_CASIFIER;
		Function<String,String> columnNamer = POSTGRES_CASIFIER;
		
		HashMap<String,String> namespacePrefixes = new HashMap<String,String>();
		namespacePrefixes.put("rdf", Core.RDF_NS.prefix);
		namespacePrefixes.put("rdfs", Core.RDFS_NS.prefix);
		namespacePrefixes.put("schema", Core.NS.prefix);
		namespacePrefixes.put("types", Types.NS.prefix);
		namespacePrefixes.put("rdb", RDB.NS.prefix);
		namespacePrefixes.put("app", Application.NS.prefix);
		namespacePrefixes.put("dtx", DataTypeTranslation.NS.prefix);
		
		Macros.FUNCTIONS_NS.getClass(); // To make sure it's loaded
		
		for( int i=0; i<args.length; ++i ) {
			if( "-?".equals(args[i]) || "-h".equals(args[i]) || "--help".equals(args[i]) ) {
				System.out.println(USAGE_TEXT);
				System.exit(0);
			} else if( "-dump-predefs".equals(args[i]) ) {
				dumpNamespaces(Namespace.ROOT);
				System.exit(1);
			} else if( "-namespace-abbreviation".equals(args[i]) ) {
				String abbreviation = args[++i];
				String prefix = args[++i];
				namespacePrefixes.put(abbreviation, prefix);
			} else if( "-o-schema-php".equals(args[i]) ) {
				outputSchemaPhpFile = new File(args[++i]);
			} else if( "-o-schema-rdf".equals(args[i]) ) {
				outputSchemaRdfFile = new File(args[++i]);
			} else if( "-php-schema-class-namespace".equals(args[i]) ) {
				phpSchemaClassNamespace = args[++i];
			} else if( "-o-db-scripts".equals(args[i]) ) {
				File dir = new File(args[++i]);
				outputCreateTablesScriptFile = new File(dir, "create-tables.sql");
				outputDropTablesScriptFile = new File(dir, "drop-tables.sql");
			} else if( "-o-create-tables-script".equals(args[i]) ) {
				outputCreateTablesScriptFile = new File(args[++i]);
			} else if( "-o-drop-tables-script".equals(args[i]) ) {
				outputDropTablesScriptFile = new File(args[++i]);
			} else if( !args[i].startsWith("-") ) {
				sourceFilenames.add(args[i]);
			} else {
				System.err.println("Unrecognized argument: "+args[i]);
				System.err.println(USAGE_TEXT);
				System.exit(1);
			}
		}
		
		boolean didSomething = false;
		
		SchemaInterpreter sp = new SchemaInterpreter();
		sp.defineImportable( Application.NS );
		sp.defineImportable( Core.NS );
		sp.defineImportable( Core.RDF_NS );
		sp.defineImportable( Core.RDFS_NS );
		sp.defineImportable( Core.SCHEMA_NS );
		sp.defineImportable( DataTypeTranslation.NS );
		sp.defineImportable( RDB.NS );
		sp.defineImportable( Types.NS );
		sp.defineImportable( Macros.FUNCTIONS_NS );
		
		sp.defineFieldModifier("key", SchemaInterpreter.FieldIndexModifierSpec.INSTANCE );
		sp.defineFieldModifier("index", SchemaInterpreter.FieldIndexModifierSpec.INSTANCE );
		sp.defineFieldModifier("enum", new SchemaInterpreter.EnumModifierSpec(Core.VALUE_TYPE) );
		
		CommandInterpreters.defineTypeDefinitionCommands(sp);
		CommandInterpreters.defineExtensionCommand(sp);
		CommandInterpreters.defineImportCommand(sp);
		CommandInterpreters.defineAliasCommand(sp);
		CommandInterpreters.defineUndefineCommand(sp);
		
		TableClassFilter<CompileError> tableClassFilter = new TableClassFilter<CompileError>();
		ClassClassFilter<ComplexType, CompileError> complexTypeFilter = new ClassClassFilter<ComplexType, CompileError>(ComplexType.class); 
		
		if( outputCreateTablesScriptFile != null ) {
			// TODO: Also output CREATE SEQUENCE stuff
			FileUtil.mkParentDirs(outputCreateTablesScriptFile);
			final FileWriter createTablesWriter = new FileWriter(outputCreateTablesScriptFile);
			final SQLEmitter createTablesSqlEmitter = new SQLEmitter(createTablesWriter);
			final TableCreationSQLGenerator tcsg = new TableCreationSQLGenerator(createTablesSqlEmitter, tableNamer, columnNamer);
			tcsg.pipe(new StreamDestination<TableDefinition, CompileError>() {
				@Override public void data(TableDefinition td) throws CompileError {
					try {
						createTablesSqlEmitter.emitTableCreation(td);
					} catch( CompileError e ) {
						throw e;
					} catch( Exception e ) {
						throw new CompileError(e, BaseSourceLocation.NONE);
					}
				};
				@Override public void end() throws CompileError {
					try {
						createTablesWriter.close();
					} catch( Exception e ) {
						throw new CompileError(e, BaseSourceLocation.NONE);
					}
				}
			});
			tableClassFilter.pipe(tcsg);
			didSomething = true;
		}
		
		if( outputDropTablesScriptFile != null ) {
			FileUtil.mkParentDirs(outputDropTablesScriptFile);
			final ArrayList<String[]> tableList = new ArrayList<String[]>();
			final FileWriter dropTablesWriter = new FileWriter(outputDropTablesScriptFile);
			final SQLEmitter dropTablesSqlEmitter = new SQLEmitter(dropTablesWriter);
			final TableCreationSQLGenerator tcsg = new TableCreationSQLGenerator(dropTablesSqlEmitter, tableNamer, columnNamer);
			tcsg.pipe(new StreamDestination<TableDefinition, CompileError>() {
				@Override public void data(TableDefinition td) throws CompileError {
					tableList.add(td.path);
				}
				@Override public void end() throws CompileError {
					try {
						Collections.reverse(tableList);
						for( String[] tablePath: tableList ) {
							dropTablesSqlEmitter.emitDropTable(tablePath);
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
			didSomething = true;
		}
		
		if( outputSchemaRdfFile != null ) {
			FileUtil.mkParentDirs(outputSchemaRdfFile);
			
			final FileWriter rdfSchemaWriter = new FileWriter(outputSchemaRdfFile);
			final SchemaRDFGenerator srg = new SchemaRDFGenerator(rdfSchemaWriter, namespacePrefixes);
			sp.pipe(new StreamDestination<SchemaObject, CompileError>() {
				@Override public void data(SchemaObject value) throws CompileError {
					try {
						srg.data(value);
					} catch (IOException e) {
						throw new CompileError(e, BaseSourceLocation.NONE);
					}
				}
				@Override public void end() throws CompileError {
					try {
						srg.end();
						rdfSchemaWriter.close();
					} catch (IOException e) {
						throw new CompileError(e, BaseSourceLocation.NONE);
					}
				}
			});
			didSomething = true;
		}
		
		if( outputSchemaPhpFile != null ) {
			FileUtil.mkParentDirs(outputSchemaPhpFile);
			
			final FileWriter phpSchemaWriter = new FileWriter(outputSchemaPhpFile);
			final PHPSchemaDumper psd = new PHPSchemaDumper(phpSchemaWriter, phpSchemaClassNamespace);
			complexTypeFilter.pipe(new StreamDestination<ComplexType, CompileError>() {
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
			didSomething = true;
		}
		
		sp.pipe( tableClassFilter );
		sp.pipe( complexTypeFilter );
		
		// Since we're potentially parsing multiple files,
		// we need to set up our own tokenizer and parser.
		
		Parser p = new Parser();
		p.pipe(sp);
		
		Tokenizer t = new Tokenizer();
		t.pipe(p);
		
		for( String sourceFilename : sourceFilenames ) {
			boolean isStdin = "-".equals(sourceFilename);
			final InputStream sourceStream = isStdin ? System.in : new FileInputStream(sourceFilename);
			final Reader sourceReader = new InputStreamReader(sourceStream, "UTF-8");
			
			try {
				t.setSourceLocation( sourceFilename, 1, 1 );
				try {
					StreamUtil.pipe( sourceReader, t, false );
				} catch( CompileError e ) {
					throw e;
				} catch( IOException e ) {
					throw new RuntimeException(e);
				}
			} catch( ScriptError e ) {
				System.err.println("Script error at ");
				for( SourceLocation sLoc : e.getScriptTrace() ) {
					System.err.println("  "+BaseSourceLocation.toString(sLoc));
				}
				e.printStackTrace();
				System.exit(1);
			} finally {
				if( !isStdin ) sourceStream.close();
			}
		}
		t.end();
		
		if( sourceFilenames.size() == 0 ) {
			System.err.println("Warning: Read no inputs.  Maybe you want to read from stdin by specifying '-'?");
		}
		if( !didSomething ) {
			System.err.println("Warning: Didn't do anything.  Maybe you want to -o-<something>");
		}
	}
	
	public static void main( String[] args ) {
		try {
			_main(args);
		} catch( Exception e ) {
			e.printStackTrace();
			System.exit(1);
		}
	}
}
