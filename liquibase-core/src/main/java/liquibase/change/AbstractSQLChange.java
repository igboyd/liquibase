package liquibase.change;

import liquibase.database.Database;
import liquibase.database.core.MSSQLDatabase;
import liquibase.exception.DatabaseException;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.RawSqlStatement;
import liquibase.util.StringUtils;

import java.util.*;

/**
 * A common parent for all raw SQL related changes regardless of where the sql was sourced from.
 * 
 * Implements the necessary logic to choose how the SQL string should be parsed to generate the statements.
 *
 */
public abstract class AbstractSQLChange extends AbstractChange {

    private boolean stripComments;
    private boolean splitStatements;
    private String endDelimiter;
    private String sql;

    protected AbstractSQLChange() {
        setStripComments(null);
        setSplitStatements(null);
    }

    /**
     * {@inheritDoc}
     * @param database
     * @return
     */
    @Override
    public boolean supports(Database database) {
        return true;
    }

    /**
     * Return if comments should be stripped from the SQL before passing it to the database.
     * <p></p>
     * This will always return a non-null value and should be a boolean rather than a Boolean, but that breaks the Bean Standard.
     */
    public Boolean isStripComments() {
        return stripComments;
    }


    /**
     * Return true if comments should be stripped from the SQL before passing it to the database.
     * Passing null sets stripComments to the default value (false).
     */
    public void setStripComments(Boolean stripComments) {
        if (stripComments == null) {
            this.stripComments = false;
        } else {
            this.stripComments = stripComments;
        }
    }

    /**
     * Return if the SQL should be split into multiple statements before passing it to the database.
     * By default, statements are split around ";" and "go" delimiters.
     * <p></p>
     * This will always return a non-null value and should be a boolean rather than a Boolean, but that breaks the Bean Standard.
     */
    public Boolean isSplitStatements() {
        return splitStatements;
    }

    /**
     * Set whether SQL should be split into multiple statements.
     * Passing null sets stripComments to the default value (true).
     */
    public void setSplitStatements(Boolean splitStatements) {
        if (splitStatements == null) {
            this.splitStatements = true;
        } else {
            this.splitStatements = splitStatements;
        }
    }

    /**
     * Return the raw SQL managed by this Change
     */
    @DatabaseChangeProperty(serializationType = SerializationType.DIRECT_VALUE)
    public String getSql() {
        return sql;
    }

    /**
     * Set the raw SQL managed by this Change. The passed sql is trimmed and set to null if an empty string is passed.
     */
    public void setSql(String sql) {
       this.sql = StringUtils.trimToNull(sql);
    }

    /**
     * Set the end delimiter used to split statements. Will return null if the default delimiter should be used.
     *
     * @see #splitStatements
     */
    public String getEndDelimiter() {
        return endDelimiter;
    }

    /**
     * Set the end delimiter for splitting SQL statements. Set to null to use the default delimiter.
     * @param endDelimiter
     */
    public void setEndDelimiter(String endDelimiter) {
        this.endDelimiter = endDelimiter;
    }

    /**
     * Calculates the checksum based on the contained SQL.
     *
     * @see liquibase.change.AbstractChange#generateCheckSum()
     */
    @Override
    public CheckSum generateCheckSum() {
        String sql = getSql();
        if (sql == null) {
            sql = "";
        }
        return CheckSum.compute(this.getEndDelimiter()+":"+
                this.isSplitStatements()+":"+
                this.isStripComments()+":"+
                normalizeLineEndings(sql)); //normalize line endings
    }


    /**
     * Generates one or more SqlStatements depending on how the SQL should be parsed.
     * If split statements is set to true then the SQL is split and each command is made into a separate SqlStatement.
     * <p></p>
     * If stripping comments is true then any comments are removed before the splitting is executed.
     * The set SQL is passed through the {@link java.sql.Connection#nativeSQL} method if a connection is available.
     */
    public SqlStatement[] generateStatements(Database database) {

        List<SqlStatement> returnStatements = new ArrayList<SqlStatement>();

        if (StringUtils.trimToNull(getSql()) == null) {
            return new SqlStatement[0];
        }

        String processedSQL = normalizeLineEndings(sql);
        for (String statement : StringUtils.processMutliLineSQL(processedSQL, isStripComments(), isSplitStatements(), getEndDelimiter())) {
            if (database instanceof MSSQLDatabase) {
                 statement = statement.replaceAll("\n", "\r\n");
             }

            String escapedStatement = statement;
			try {
                if (database.getConnection() != null) {
                    escapedStatement = database.getConnection().nativeSQL(statement);
                }
            } catch (DatabaseException e) {
				escapedStatement = statement;
			}

            returnStatements.add(new RawSqlStatement(escapedStatement, getEndDelimiter()));
        }

        return returnStatements.toArray(new SqlStatement[returnStatements.size()]);
    }

    private String normalizeLineEndings(String string) {
        return string.replaceAll("\r\n", "\n").replaceAll("\r", "\n");
    }

//    @Override
//    public Set<String> getSerializableFields() {
//        Set<String> fieldsToSerialize = new HashSet<String>(super.getSerializableFields());
//        fieldsToSerialize.add("splitStatements");
//        fieldsToSerialize.add("stripComments");
//        return Collections.unmodifiableSet(fieldsToSerialize);
//    }
//
//    @Override
//    public Object getSerializableFieldValue(String field) {
//        if (field.equals("splitStatements")) {
//            return isSplitStatements();
//        }
//    }
}
