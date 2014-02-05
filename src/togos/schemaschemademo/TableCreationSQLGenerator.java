package togos.schemaschemademo;

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
		
		return new ColumnDefinition(
			columnNamer.apply(fs.getName()),
			sqlType,
			isTrue(fs, Core.IS_NULLABLE),
			null //valueToExpression( getFirstInheritedValue(fs, F30Predicates.DEFAULT) )
		);
	}
	
	protected TableDefinition toTableDefinition( ComplexType ct ) {
		TableDefinition td = new TableDefinition( tableNamer.apply(ct.getName()) );
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
			ArrayList<String> localColumnNames = new ArrayList<String>();
			ArrayList<String> foreignColumnNames = new ArrayList<String>();
			for( ForeignKeySpec.Component c : fks.components ) {
				localColumnNames.add( columnNamer.apply(c.localField.getName()) );
				foreignColumnNames.add( columnNamer.apply(c.targetField.getName()) );
			}
			td.foreignKeyConstraints.add(new ForeignKeyConstraint(
				null, localColumnNames,
				tableNamer.apply(fks.target.getName()), foreignColumnNames
			));
		}
		return td;
	}
	
	@Override
	public void data(ComplexType ct) throws CompileError {
		_data(toTableDefinition(ct));
	}
}
