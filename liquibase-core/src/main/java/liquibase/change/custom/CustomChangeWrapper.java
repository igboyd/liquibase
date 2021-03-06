package liquibase.change.custom;

import liquibase.change.AbstractChange;
import liquibase.change.DatabaseChange;
import liquibase.change.ChangeMetaData;
import liquibase.change.DatabaseChangeProperty;
import liquibase.database.Database;
import liquibase.exception.*;
import liquibase.serializer.LiquibaseSerializable;
import liquibase.statement.SqlStatement;
import liquibase.util.ObjectUtil;

import java.util.*;

/**
 * Adapts CustomChange implementations to the standard change system used by Liquibase.
 * Custom change implementations should implement CustomSqlChange or CustomTaskChange
 *
 * @see liquibase.change.custom.CustomSqlChange
 * @see liquibase.change.custom.CustomTaskChange
 */
@DatabaseChange(name="customChange", description = "Custom Change", priority = ChangeMetaData.PRIORITY_DEFAULT)
public class CustomChangeWrapper extends AbstractChange {

    /**
     * Non-private access only for testing.
     */
    CustomChange customChange;
    
    private String className;

    private SortedSet<String> params = new TreeSet<String>();

    private Map<String, String> paramValues = new HashMap<String, String>();

    private ClassLoader classLoader;

    /**
     * Return the CustomChange instance created by the call to {@link #setClass(String)}.
     */
    @DatabaseChangeProperty(isChangeProperty = false)
    public CustomChange getCustomChange() {
        return customChange;
    }

    /**
     * Returns the classloader to use when creating the CustomChange instance in {@link #setClass(String)}.
     */
    @DatabaseChangeProperty(isChangeProperty = false)
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Specify the name of the class to use as the CustomChange. This method instantiates the class using {@link #getClassLoader()} or fallback methods
     * and assigns it to {@link #getCustomChange()}.
     * {@link #setClassLoader(ClassLoader)} must be called before this method. The passed class is constructed, but no parameters are set. They are set in {@link #generateStatements(liquibase.database.Database)}
     */
    public CustomChangeWrapper setClass(String className) throws CustomChangeException {
        if (classLoader == null) {
            throw new CustomChangeException("CustomChangeWrapper classLoader not set");
        }
        this.className = className;
            try {
                try {
                    customChange = (CustomChange) Class.forName(className, true, classLoader).newInstance();
                } catch (ClassCastException e) { //fails in Ant in particular
                    try {
                        customChange = (CustomChange) Thread.currentThread().getContextClassLoader().loadClass(className).newInstance();
                    } catch (ClassNotFoundException e1) {
                        customChange = (CustomChange) Class.forName(className).newInstance();
                    }
                }
        } catch (Exception e) {
            throw new CustomChangeException(e);
        }

        return this;
    }

    /**
     * Return the name of the custom class set in {@link #setClass(String)}
     * @return
     */
    public String getClassName() {
        return className;
    }

    /**
     * Specify a parameter on the CustomChange object to set before executing {@link #generateStatements(liquibase.database.Database)}  or {@link #generateRollbackStatements(liquibase.database.Database)} on it.
     * The CustomChange class must have a set method for the given parameter. For example, to call setParam("lastName", "X") you must have a method setLastName(String val) on your class.
     */
    public void setParam(String name, String value) {
        this.params.add(name);
        this.paramValues.put(name, value);
    }

    /**
     * Returns the parameters set by {@link #setParam(String, String)}. If no parameters are set, an empty set will be returned
     */
    @DatabaseChangeProperty(isChangeProperty = false)
    public SortedSet<String> getParams() {
        return Collections.unmodifiableSortedSet(params);
    }

    /**
     * Get the value of a parameter set by {@link #setParam(String, String)}. If the parameter was not set, null will be returned.
     */
    public String getParamValue(String key) {
        return paramValues.get(key);
    }

    /**
     * Call the {@link CustomChange#validate(liquibase.database.Database)} method and return the result.
     */
    @Override
    public ValidationErrors validate(Database database) {
        try {
            return customChange.validate(database);
        } catch (Throwable e) {
            return new ValidationErrors().addError("Exception thrown calling "+getClassName()+".validate():"+ e.getMessage());
        }
    }

    /**
     * Required for the Change interface, but not supported by CustomChanges. Returns an empty Warnings object.
     */
    @Override
    public Warnings warn(Database database) {
        //does not support warns
        return new Warnings();
    }

    /**
     * Finishes configuring the CustomChange based on the values passed to {@link #setParam(String, String)} then calls {@link CustomSqlChange#generateStatements(liquibase.database.Database)}
     * or {@link CustomTaskChange#execute(liquibase.database.Database)} depending on the CustomChange implementation.
     * <p></p>
     * If the CustomChange returns a null SqlStatement array, this method returns an empty array. If a CustomTaskChange is being used, this method will return an empty array.
     */
    public SqlStatement[] generateStatements(Database database) {
        SqlStatement[] statements = null;
        try {
            configureCustomChange();
            if (customChange instanceof CustomSqlChange) {
                statements = ((CustomSqlChange) customChange).generateStatements(database);
            } else if (customChange instanceof CustomTaskChange) {
                ((CustomTaskChange) customChange).execute(database);
            } else {
                throw new UnexpectedLiquibaseException(customChange.getClass().getName() + " does not implement " + CustomSqlChange.class.getName() + " or " + CustomTaskChange.class.getName());
            }
        } catch (CustomChangeException e) {
            throw new UnexpectedLiquibaseException(e);
        }

        if (statements == null) {
            statements = new SqlStatement[0];
        }
        return statements;
    }

    /**
     * Finishes configuring the CustomChange based on the values passed to {@link #setParam(String, String)} then calls {@link CustomSqlRollback#generateRollbackStatements(liquibase.database.Database)}
     * or {@link CustomTaskRollback#rollback(liquibase.database.Database)} depending on the CustomChange implementation.
     * <p></p>
     * If the CustomChange returns a null SqlStatement array, this method returns an empty array. If a CustomTaskChange is being used, this method will return an empty array.
     * Any {@link RollbackImpossibleException} exceptions thrown by the CustomChange will thrown by this method.
     */
    @Override
    public SqlStatement[] generateRollbackStatements(Database database) throws RollbackImpossibleException {
        SqlStatement[] statements = null;
        try {
            configureCustomChange();
            if (customChange instanceof CustomSqlRollback) {
                statements = ((CustomSqlRollback) customChange).generateRollbackStatements(database);
            } else if (customChange instanceof CustomTaskRollback) {
                ((CustomTaskRollback) customChange).rollback(database);
            } else {
                throw new RollbackImpossibleException("Unknown rollback type: "+customChange.getClass().getName());
            }
        } catch (CustomChangeException e) {
            throw new UnexpectedLiquibaseException(e);
        }

        if (statements == null) {
            statements = new SqlStatement[0];
        }
        return statements;

    }


    /**
     * Returns true if the customChange supports rolling back.
     * {@link #generateRollbackStatements} may still trow a {@link RollbackImpossibleException} when it is actually exectued, even if this method returns true.
     * Currently only checks if the customChange implements {@link CustomSqlRollback}
     */
    @Override
    public boolean supportsRollback(Database database) {
        return customChange instanceof CustomSqlRollback || customChange instanceof CustomTaskRollback;
    }

    /**
     * Return the customChange's {@link CustomChange#getConfirmationMessage} message as the Change's message.
     */
    public String getConfirmationMessage() {
        return customChange.getConfirmationMessage();
    }

    private void configureCustomChange() throws CustomChangeException {
        try {
            for (String param : params) {
                ObjectUtil.setProperty(customChange, param, paramValues.get(param));
            }
            customChange.setFileOpener(getResourceAccessor());
            customChange.setUp();
        } catch (Exception e) {
            throw new CustomChangeException(e);
        }
    }

    @Override
    public SerializationType getSerializableFieldType(String field) {
        if (field.equals("class")) {
            return SerializationType.NAMED_FIELD;
        } else if (field.equals("param")) {
            return SerializationType.NESTED_OBJECT;
        } else {
            throw new UnexpectedLiquibaseException("Unexpected CustomChangeWrapper field "+field);
        }
    }

    @Override
    public Object getSerializableFieldValue(String field) {
        if (field.equals("class")) {
            return getClassName();
        } else if (field.equals("param")) {
            return this.paramValues;
        } else {
            throw new UnexpectedLiquibaseException("Unexpected CustomChangeWrapper field "+field);
        }
    }

    @Override
    public Set<String> getSerializableFields() {
        return new HashSet<String>(Arrays.asList("class", "param"));
    }
}
