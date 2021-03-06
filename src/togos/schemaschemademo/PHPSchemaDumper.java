package togos.schemaschemademo;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import togos.asyncstream.StreamDestination;
import togos.codeemitter.TextWriter;
import togos.schemaschema.ComplexType;
import togos.schemaschema.EnumType;
import togos.schemaschema.FieldSpec;
import togos.schemaschema.ForeignKeySpec;
import togos.schemaschema.IndexSpec;
import togos.schemaschema.Predicate;
import togos.schemaschema.PropertyUtil;
import togos.schemaschema.SchemaObject;
import togos.schemaschema.Type;
import togos.schemaschema.namespaces.Application;
import togos.schemaschema.namespaces.Core;
import togos.schemaschema.namespaces.DataTypeTranslation;
import togos.schemaschema.namespaces.RDB;

public class PHPSchemaDumper implements StreamDestination<ComplexType, Exception>
{
	protected final TextWriter tw;
	protected final String schemaClassNamespace;
	boolean itemWritten = false;
	
	public PHPSchemaDumper( Appendable dest, String schemaClassNamespace ) {
		this.tw = new TextWriter(dest);
		this.schemaClassNamespace = schemaClassNamespace;
	}
	
	protected void openArray() throws IOException {
		tw.write("array(");
		tw.indentMore();
		itemWritten = false;
	}
	
	protected void closeArray() throws IOException {
		tw.indentLess();
		if( itemWritten ) {
			tw.endLine();
			tw.writeIndent();
		}
		tw.write(")");
		itemWritten = true;
	}
	
	protected void openObject(String className) throws IOException {
		tw.write(className+"::__set_state(array(");
		tw.indentMore();
		itemWritten = false;
	}
	
	protected void preItem() throws IOException {
		if( itemWritten ) tw.write(",");
		tw.endLine();
		tw.writeIndent();
	}
	
	protected void writeKey( String k ) throws IOException {
		preItem();
		writeScalar(k);
		tw.write(" => ");
	}
	
	protected void writeScalar( Object o ) throws IOException {
		if( o == null ) {
			tw.write("null");
		} else if( o instanceof String ) {
			tw.write('"' + o.toString().replace("\\", "\\\\").replace("\"", "\\\"") + '"');
		} else if( o instanceof Number ) {
			tw.write(o.toString());
		} else if( o instanceof Boolean ) {
			tw.write( ((Boolean)o).booleanValue() ? "true" : "false" );
		} else {
			throw new RuntimeException("Don't know how to compile "+o.getClass()+" to PHP literal");
		}
	}
	
	protected void writeItemValue( Object o ) throws IOException {
		writeScalar(o);
		itemWritten = true;
	}
	
	protected void writePair( String k, Object v ) throws IOException {
		preItem();
		writeScalar(k);
		tw.write(" => ");
		writeItemValue(v);
	}
	
	protected void closeObject() throws IOException {
		tw.indentLess();
		if( itemWritten ) {
			tw.endLine();
			tw.writeIndent();
		}
		tw.write("))");
		itemWritten = true;
	}
	
	protected void writeDataType( Type t ) throws IOException {
		String name = t.getName();
		String sqlType = PropertyUtil.getFirstInheritedScalar(t, DataTypeTranslation.SQL_TYPE, String.class, null);
		String jsonType = PropertyUtil.getFirstInheritedScalar(t, DataTypeTranslation.JSON_TYPE, String.class, null);
		String phpType = PropertyUtil.getFirstInheritedScalar(t, DataTypeTranslation.PHP_TYPE, String.class, null);
		String regex = PropertyUtil.getFirstInheritedScalar(t, DataTypeTranslation.REGEX, String.class, null);
		
		if( t instanceof EnumType ) {
			if( jsonType == null ) jsonType = "string";
			if( phpType  == null ) jsonType = "string";
		}
		
		openObject(schemaClassNamespace+"_DataType");
		writeKey("name"); writeItemValue(name);
		writeKey("sqlTypeName"); writeItemValue(sqlType);
		writeKey("phpTypeName"); writeItemValue(phpType);
		writeKey("jsTypeName"); writeItemValue(jsonType);
		if( regex != null ) {
			writeKey("regex");
			writeItemValue(regex);
		}
		writeKey("properties"); writeSimpleProperties( t );
		closeObject();
	}
	
	protected void writeField( FieldSpec fs ) throws IOException {
		boolean isNullable = PropertyUtil.getFirstInheritedBoolean(fs, Core.IS_NULLABLE, false);
		String columnNameOverride = PropertyUtil.getFirstInheritedScalar(fs, RDB.NAME_IN_DB, String.class, null);
		
		openObject(schemaClassNamespace+"_Field");
		writePair("name", fs.getName());
		writePair("columnNameOverride", columnNameOverride);
		writePair("isNullable", isNullable);
		writeKey("properties"); writeSimpleProperties( fs );
		writeKey("type");
		writeDataType(fs.getObjectType());
		closeObject();
	}
	
	protected void writeReference( ForeignKeySpec fk ) throws IOException {
		openObject(schemaClassNamespace+"_Reference");
		writePair("name", fk.getName());
		writeKey("targetClassName"); writeItemValue(fk.target.getName());
		writeKey("originFieldNames"); openArray();
		for( ForeignKeySpec.Component fkc : fk.components ) {
			preItem(); writeItemValue(fkc.localField.getName());
		}
		closeArray();
		writeKey("targetFieldNames"); openArray();
		for( ForeignKeySpec.Component fkc : fk.components ) {
			preItem(); writeItemValue(fkc.targetField.getName());
		}
		closeArray();
		writeKey("properties"); writeSimpleProperties( fk );
		closeObject();
	}
	
	protected void writeIndex( IndexSpec is ) throws IOException {
		openObject(schemaClassNamespace+"_Index");
		writeKey("fieldNames"); openArray();
		for( FieldSpec f : is.fields ) {
			preItem(); writeItemValue(f.getName());
		}
		closeArray();
		closeObject();
	}
	
	protected boolean anyValuesHaveSimpleRepresentations( Set<SchemaObject> schemaObjects ) {
		for( SchemaObject v : schemaObjects ) {
			if( v.getScalarValue() != null || v.getLongName() != null ) return true;
		}
		return false;
	}
	
	protected void writeSimpleProperties( SchemaObject obj ) throws IOException {
		openArray();
		for( Map.Entry<Predicate,Set<SchemaObject>> kv : obj.getProperties().entrySet() ) {
			if( kv.getKey().getLongName() != null && anyValuesHaveSimpleRepresentations(kv.getValue()) ) {
				writeKey( kv.getKey().getLongName() );
				openArray();
				for( SchemaObject v : kv.getValue() ) {
					if( v.getScalarValue() != null ) {
						preItem();
						writeItemValue( v.getScalarValue() );
					} else if( v.getLongName() != null ) {
						preItem();
						openArray();
						writeKey("uri");
						writeItemValue(v.getLongName());
						closeArray();
					}
				}
				closeArray();
			}
		}
		closeArray();
	}
	
	protected void writeClassSchema( ComplexType type ) throws Exception {
		boolean hasRestService = PropertyUtil.getFirstInheritedBoolean(type, Application.HAS_REST_SERVICE, false);
		boolean hasDbTable = PropertyUtil.getFirstInheritedBoolean(type, Application.HAS_DB_TABLE, false);
		boolean memberSetIsMutable = PropertyUtil.getFirstInheritedBoolean(type, Application.MEMBER_SET_IS_MUTABLE, false);
		boolean membersAreMutable = PropertyUtil.getFirstInheritedBoolean(type, Application.MEMBERS_ARE_MUTABLE, false);
		boolean membersArePublic = PropertyUtil.getFirstInheritedBoolean(type, Application.MEMBERS_ARE_PUBLIC, false);
		String tableNameOverride = PropertyUtil.getFirstInheritedScalar(type, RDB.NAME_IN_DB, String.class, null);
		
		openObject(schemaClassNamespace+"_ResourceClass");
		
		writePair("name", type.getName());
		writePair("hasDbTable", hasDbTable);
		writePair("hasRestService", hasRestService);
		writePair("memberSetIsMutable", memberSetIsMutable);
		writePair("membersAreMutable", membersAreMutable);
		writePair("membersArePublic", membersArePublic);
		writePair("tableNameOverride", tableNameOverride);
		writeKey("dbNamespacePath");
		openArray();
		SchemaObject namespace = PropertyUtil.getFirstInheritedValue(type, RDB.IN_NAMESPACE, null);
		if( namespace != null ) {
			String namespaceName = PropertyUtil.getFirstInheritedScalar(namespace, RDB.NAME_IN_DB, String.class, null);
			if( namespaceName == null ) namespaceName = namespace.getName().toLowerCase().replaceAll("[^a-z0-9_]","");
			preItem();
			writeItemValue(namespaceName);
		}
		closeArray();
		
		writeKey("properties"); writeSimpleProperties( type );
		
		writeKey("fields"); openArray();
		for( FieldSpec fs : type.getFields() ) {
			writeKey(fs.getName());
			writeField(fs);
		}
		closeArray();
		
		writeKey("references"); openArray();
		for( ForeignKeySpec fk : type.getForeignKeys() ) {
			writeKey(fk.getName());
			writeReference(fk);
		}
		closeArray();
		
		writeKey("indexes"); openArray();
		for( IndexSpec is : type.getIndexes() ) {
			writeKey(is.getName());
			writeIndex(is);
		}
		closeArray();
		
		closeObject();
	}
	
	boolean opened = false;
	
	protected void ensureSchemaOpened() throws IOException {
		if( !opened ) {
			tw.write("<?php\n");
			tw.write("\n");
			tw.write("// This file was generated automatically.\n");
			tw.write("// Don't make changes to it directly unless you like your changes being overwritten.\n");
			tw.write("\n");
			tw.write("return ");
			openObject(schemaClassNamespace);
			writeKey("resourceClasses");
			openArray();
			opened = true;
		}
	}
	
	@Override public void data(ComplexType type) throws Exception {
		ensureSchemaOpened();
		writeKey(type.getName());
		writeClassSchema(type);
	}
	
	@Override public void end() throws Exception {
		ensureSchemaOpened();
		closeArray();
		closeObject();
		tw.write(";");
		tw.endLine();
	}
}
