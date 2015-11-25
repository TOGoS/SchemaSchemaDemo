package togos.schemaschemademo;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

import junit.framework.TestCase;
import togos.asyncstream.StreamDestination;
import togos.asyncstream.StreamUtil;
import togos.lang.BaseSourceLocation;
import togos.lang.CompileError;
import togos.lang.ScriptError;
import togos.schemaschema.SchemaObject;
import togos.schemaschema.namespaces.Application;
import togos.schemaschema.namespaces.Core;
import togos.schemaschema.namespaces.DataTypeTranslation;
import togos.schemaschema.namespaces.RDB;
import togos.schemaschema.namespaces.Types;
import togos.schemaschema.parser.CommandInterpreters;
import togos.schemaschema.parser.Parser;
import togos.schemaschema.parser.SchemaInterpreter;
import togos.schemaschema.parser.Tokenizer;

public class SchemaRDFificationTest extends TestCase {
	protected String toRdf(File schemaTxtFile) throws ScriptError, IOException {
		HashMap<String,String> namespacePrefixes = new HashMap<String,String>();
		namespacePrefixes.put("rdf", Core.RDF_NS.prefix);
		namespacePrefixes.put("rdfs", Core.RDFS_NS.prefix);
		namespacePrefixes.put("schema", Core.NS.prefix);
		namespacePrefixes.put("types", Types.NS.prefix);
		namespacePrefixes.put("rdb", RDB.NS.prefix);
		namespacePrefixes.put("app", Application.NS.prefix);
		namespacePrefixes.put("dtx", DataTypeTranslation.NS.prefix);
		namespacePrefixes.put("example", "http://ns.example.com/");
		
		final StringWriter sw = new StringWriter();
		SchemaInterpreter sp = new SchemaInterpreter();
		sp.defineImportable( Application.NS );
		sp.defineImportable( Core.NS );
		sp.defineImportable( Core.RDF_NS );
		sp.defineImportable( Core.RDFS_NS );
		sp.defineImportable( Core.SCHEMA_NS );
		sp.defineImportable( DataTypeTranslation.NS );
		sp.defineImportable( RDB.NS );
		sp.defineImportable( Types.NS );
		CommandInterpreters.defineTypeDefinitionCommands(sp);
		CommandInterpreters.defineExtensionCommand(sp);
		CommandInterpreters.defineImportCommand(sp);
		CommandInterpreters.defineAliasCommand(sp);
		
		final SchemaRDFGenerator srg = new SchemaRDFGenerator(sw, namespacePrefixes);
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
					sw.append('\n');
					sw.close();
				} catch (IOException e) {
					throw new CompileError(e, BaseSourceLocation.NONE);
				}
			}
		});

		Parser p = new Parser();
		p.pipe(sp);
		
		Tokenizer t = new Tokenizer();
		t.pipe(p);
		
		t.setSourceLocation( schemaTxtFile.getPath(), 1, 1 );
		FileReader r = new FileReader(schemaTxtFile);
		try {
			StreamUtil.pipe( r, t, true );
		} finally {
			r.close();
		}
		
		return sw.toString();
	}
	
	protected String fileContent(File file) throws IOException {
		StringWriter sw = new StringWriter();
		FileReader fr = new FileReader(file);
		char[] buffer = new char[1024];
		try {
			int z;
			while( (z = fr.read(buffer)) > 0 ) {
				sw.write(buffer, 0, z);
			}
		} finally {
			fr.close();
		}
		return sw.toString();
	}
	
	protected void assertRdfifiesTo(File rdfFile, File schemaTxtFile) throws IOException, ScriptError {
		String expectedRdf = fileContent(rdfFile);
		assertEquals( expectedRdf, toRdf(schemaTxtFile) );
	}
	
	public void testThing1() throws ScriptError, IOException {
		assertRdfifiesTo( new File("test/thing1.rdf"), new File("test/thing1.txt") );
	}
	public void testThing2() throws ScriptError, IOException {
		assertRdfifiesTo( new File("test/thing2.rdf"), new File("test/thing2.txt") );
	}
}
