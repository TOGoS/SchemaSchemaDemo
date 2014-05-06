package togos.schemaschemademo;

import static togos.schemaschema.PropertyUtil.getFirstInheritedBoolean;
import static togos.schemaschema.PropertyUtil.getFirstInheritedScalar;
import static togos.schemaschema.PropertyUtil.getFirstInheritedValue;
import static togos.schemaschema.PropertyUtil.isTrue;

import java.util.ArrayList;

import togos.asyncstream.BaseStreamSource;
import togos.asyncstream.StreamDestination;
import togos.codeemitter.sql.SQLEmitter;
import togos.codeemitter.structure.Expression;
import togos.codeemitter.structure.ScalarLiteral;
import togos.codeemitter.structure.rdb.ColumnDefinition;
import togos.codeemitter.structure.rdb.ForeignKeyConstraint;
import togos.codeemitter.structure.rdb.IndexDefinition;
import togos.codeemitter.structure.rdb.NextAutoIncrementValueExpression;
import togos.codeemitter.structure.rdb.NextSequenceValueExpression;
import togos.codeemitter.structure.rdb.TableDefinition;
import togos.function.Function;
import togos.lang.CompileError;
import togos.schemaschema.ComplexType;
import togos.schemaschema.EnumType;
import togos.schemaschema.FieldSpec;
import togos.schemaschema.ForeignKeySpec;
import togos.schemaschema.IndexSpec;
import togos.schemaschema.SchemaObject;
import togos.schemaschema.namespaces.Core;
import togos.schemaschema.namespaces.DataTypeTranslation;
import togos.schemaschema.namespaces.RDB;

public class TableCreationSQLGenerator extends BaseStreamSource<TableDefinition, CompileError> implements StreamDestination<ComplexType, CompileError>
{
	final SQLEmitter sqlEmitter; // Only needed to encode values for enums. TODO: Let it encode them itself.
	final Function<String,String> tableNamer;
	final Function<String,String> columnNamer;
	
	public TableCreationSQLGenerator( SQLEmitter sqlEmitter, Function<String,String> tableNamer, Function<String,String> columnNamer ) {
		this.sqlEmitter = sqlEmitter;
		this.tableNamer = tableNamer;
		this.columnNamer = columnNamer;
	}
	
	protected Expression valueToExpression( SchemaObject obj ) {
		return new ScalarLiteral(obj.getScalarValue(), obj.getSourceLocation());
	}
	
	protected ColumnDefinition toColumnDefinition( FieldSpec fs ) {
		SchemaObject objectType = getFirstInheritedValue( fs, Core.VALUE_TYPE );
		
		String sqlType;
		if( isTrue( objectType, Core.IS_ENUM_TYPE ) ) {
			// TODO: Shouldn't require it to actually be an EnumType object;
			// valid values should be represented as properties.
			EnumType enumType = (EnumType)objectType;
			sqlType = "";
			for( String name : enumType.getValidValueNames() ) {
				if( sqlType.length() > 0 ) sqlType += ", ";
				sqlType += sqlEmitter.quoteText(name);
			}
			sqlType = "ENUM(" + sqlType + ")";
		} else {
			sqlType = getFirstInheritedScalar(
				objectType,
				DataTypeTranslation.SQL_TYPE,
				String.class,
				null
			);
		}
		
		if( sqlType == null ) {
			throw new RuntimeException("Field '"+fs.getName()+"' has no SQL type defined and is not an enum");
		}
		
		Expression defaultValueExpression;
		SchemaObject sequence;
		if( (sequence = getFirstInheritedValue(fs, RDB.DEFAULT_VALUE_SEQUENCE, null)) != null ) {
			defaultValueExpression = new NextSequenceValueExpression(tableNamer.apply(sequence.getName()));
		} else if( getFirstInheritedBoolean(fs, RDB.IS_AUTO_INCREMENTED, false) ) {
			defaultValueExpression = NextAutoIncrementValueExpression.INSTANCE;
		} else {
			defaultValueExpression = null;
		}
		
		String columnName = getFirstInheritedScalar(fs, RDB.NAME_IN_DB, String.class, null);
		if( columnName == null ) columnName = columnNamer.apply(fs.getName()); 
		
		return new ColumnDefinition(
			columnName,
			sqlType,
			isTrue(fs, Core.IS_NULLABLE),
			defaultValueExpression
		);
	}
	
	protected TableDefinition toTableDefinition( ComplexType ct ) {
		String tableName = getFirstInheritedScalar(ct, RDB.NAME_IN_DB, String.class, null);
		if( tableName == null ) tableName = tableNamer.apply(ct.getName()); 
		
		TableDefinition td = new TableDefinition( tableName );
		for( FieldSpec fs : ct.getFields() ) {
			td.columns.add(toColumnDefinition(fs));
		}
		for( IndexSpec is : ct.getIndexes() ) {
			ArrayList<String> indexColumnNames = new ArrayList<String>();
			for( FieldSpec fs : is.fields ) {
				indexColumnNames.add( columnNamer.apply(fs.getName()) );
			}
			if("primary".equals(is.getName()) ) {
				td.primaryKeyColumnNames =  indexColumnNames;
			} else {
				td.indexes.add(new IndexDefinition(
					is.getName(), indexColumnNames
				));
			}
		}
		for( ForeignKeySpec fks : ct.getForeignKeys() ) {
			String targetTableName = tableNamer.apply(fks.target.getName());
			ArrayList<String> localColumnNames = new ArrayList<String>();
			ArrayList<String> foreignColumnNames = new ArrayList<String>();
			for( ForeignKeySpec.Component c : fks.components ) {
				localColumnNames.add( columnNamer.apply(c.localField.getName()) );
				foreignColumnNames.add( columnNamer.apply(c.targetField.getName()) );
			}
			td.foreignKeyConstraints.add(new ForeignKeyConstraint(
				null, localColumnNames, targetTableName, foreignColumnNames
			));
		}
		return td;
	}
	
	@Override
	public void data(ComplexType ct) throws CompileError {
		_data(toTableDefinition(ct));
	}
}
