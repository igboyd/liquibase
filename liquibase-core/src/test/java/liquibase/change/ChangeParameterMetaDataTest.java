package liquibase.change;

import liquibase.change.core.CreateTableChange;
import liquibase.database.core.MSSQLDatabase;
import liquibase.database.core.MySQLDatabase;
import liquibase.database.core.OracleDatabase;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.serializer.LiquibaseSerializable;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChangeParameterMetaDataTest {

    @Test
    public void constructor() {
        ChangeParameterMetaData metaData = new ChangeParameterMetaData("x", "y", "desc", "examp", Integer.class, new String[]{"mysql", "mssql"}, "column", LiquibaseSerializable.SerializationType.NESTED_OBJECT);
        assertEquals("x", metaData.getParameterName()) ;
        assertEquals("y", metaData.getDisplayName());
        assertEquals("integer", metaData.getDataType());
        assertEquals(2, metaData.getRequiredForDatabase().size());
        assertTrue(metaData.getRequiredForDatabase().contains("mysql"));
        assertTrue(metaData.getRequiredForDatabase().contains("mssql"));
        assertEquals("column", metaData.getMustEqualExisting());
        assertEquals(LiquibaseSerializable.SerializationType.NESTED_OBJECT, metaData.getSerializationType());
        assertEquals("desc", metaData.getDescription());
        assertEquals("examp", metaData.getExampleValue());
    }

    @Test
    public void constructor_badValues() {
        try {
            new ChangeParameterMetaData(null, "y", null, null,String.class, null, null, LiquibaseSerializable.SerializationType.NAMED_FIELD);
            fail("Did not throw exception");
        } catch (UnexpectedLiquibaseException e) {
            assertEquals("Unexpected null parameterName", e.getMessage());
        }

        try {
            new ChangeParameterMetaData("x tag", "y", null, null,String.class, null, null, LiquibaseSerializable.SerializationType.NAMED_FIELD);
            fail("Did not throw exception");
        } catch (UnexpectedLiquibaseException e) {
            assertEquals("Unexpected space in parameterName", e.getMessage());
        }

        try {
            new ChangeParameterMetaData("x", null, null, null,String.class, null, null, LiquibaseSerializable.SerializationType.NAMED_FIELD);
            fail("Did not throw exception");
        } catch (UnexpectedLiquibaseException e) {
            assertEquals("Unexpected null displayName", e.getMessage());
        }

        try {
            new ChangeParameterMetaData("x", "y", null, null, null, null,null, LiquibaseSerializable.SerializationType.NAMED_FIELD);
            fail("Did not throw exception");
        } catch (UnexpectedLiquibaseException e) {
            assertEquals("Unexpected null dataType", e.getMessage());
        }
    }

    @Test
    public void getRequiredForDatabase_nullPassedInReturnsEmptySet() {
        assertEquals(0, new ChangeParameterMetaData("x", "y", null, null,Integer.class, null, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).getRequiredForDatabase().size());
    }

    @Test
    public void getRequiredForDatabase_nonePassedReturnsEmptySet() {
        assertEquals(0, new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[] {"none"}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).getRequiredForDatabase().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getRequiredForDatabase_immutable() {
        new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[] {"mysql"}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).getRequiredForDatabase().add("mssql");
    }

    @Test
    public void isRequiredFor() {
        assertTrue(new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[]{"mysql"}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).isRequiredFor(new MySQLDatabase()));
        assertTrue(new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[]{"mysql"}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).isRequiredFor(new MySQLDatabase() {})); //mysql database subclass
        assertFalse(new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[]{"mysql"}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).isRequiredFor(new MSSQLDatabase()));

        assertTrue(new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[]{"mysql", "mssql"}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).isRequiredFor(new MySQLDatabase()));
        assertTrue(new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[]{"mysql", "mssql"}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).isRequiredFor(new MSSQLDatabase()));
        assertFalse(new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[]{"mysql", "mssql"}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).isRequiredFor(new OracleDatabase()));

        assertTrue(new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[]{"all"}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).isRequiredFor(new OracleDatabase()));
        assertTrue(new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[]{"all"}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).isRequiredFor(new MySQLDatabase()));

        assertFalse(new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[]{}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).isRequiredFor(new OracleDatabase()));
        assertFalse(new ChangeParameterMetaData("x", "y", null, null,Integer.class, new String[]{}, null, LiquibaseSerializable.SerializationType.NAMED_FIELD).isRequiredFor(new MySQLDatabase()));
    }

    @Test
    public void getCurrentValue() {
        CreateTableChange change = new CreateTableChange();
        change.setTableName("newTable");
        change.setCatalogName("newCatalog");

        ChangeParameterMetaData tableNameMetaData = new ChangeParameterMetaData("tableName", "New Table", null, null,String.class, null, null, LiquibaseSerializable.SerializationType.NAMED_FIELD);
        ChangeParameterMetaData catalogNameMetaData = new ChangeParameterMetaData("catalogName", "New Catalog", null, null,String.class, null, null, LiquibaseSerializable.SerializationType.NAMED_FIELD);
        ChangeParameterMetaData remarksMetaData = new ChangeParameterMetaData("remarks", "Remarks", null, null,String.class, null, null, LiquibaseSerializable.SerializationType.NAMED_FIELD);

        assertEquals("newTable", tableNameMetaData.getCurrentValue(change));
        assertEquals("newCatalog", catalogNameMetaData.getCurrentValue(change));
        assertNull(remarksMetaData.getCurrentValue(change));

        change.setTableName("changedTableName");
        assertEquals("changedTableName", tableNameMetaData.getCurrentValue(change));
    }

    @Test(expected = UnexpectedLiquibaseException.class)
    public void getCurrentValue_badParam() {
        CreateTableChange change = new CreateTableChange();
        ChangeParameterMetaData badParamMetaData = new ChangeParameterMetaData("badParameter", "Doesn't really exist", null, null,Integer.class, null, null, LiquibaseSerializable.SerializationType.NAMED_FIELD);
        badParamMetaData.getCurrentValue(change);

    }
}
