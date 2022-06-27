/*
 * This file is generated by jOOQ.
 */
package keywhiz.jooq.tables.records;


import keywhiz.jooq.tables.Clients;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record13;
import org.jooq.Row13;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ClientsRecord extends UpdatableRecordImpl<ClientsRecord> implements Record13<Long, String, Long, Long, String, String, String, Boolean, Boolean, Long, Long, String, String> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>keywhizdb_test.clients.id</code>.
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.id</code>.
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.name</code>.
     */
    public void setName(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.name</code>.
     */
    public String getName() {
        return (String) get(1);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.updatedat</code>.
     */
    public void setUpdatedat(Long value) {
        set(2, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.updatedat</code>.
     */
    public Long getUpdatedat() {
        return (Long) get(2);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.createdat</code>.
     */
    public void setCreatedat(Long value) {
        set(3, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.createdat</code>.
     */
    public Long getCreatedat() {
        return (Long) get(3);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.description</code>.
     */
    public void setDescription(String value) {
        set(4, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.description</code>.
     */
    public String getDescription() {
        return (String) get(4);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.createdby</code>.
     */
    public void setCreatedby(String value) {
        set(5, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.createdby</code>.
     */
    public String getCreatedby() {
        return (String) get(5);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.updatedby</code>.
     */
    public void setUpdatedby(String value) {
        set(6, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.updatedby</code>.
     */
    public String getUpdatedby() {
        return (String) get(6);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.enabled</code>.
     */
    public void setEnabled(Boolean value) {
        set(7, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.enabled</code>.
     */
    public Boolean getEnabled() {
        return (Boolean) get(7);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.automationallowed</code>.
     */
    public void setAutomationallowed(Boolean value) {
        set(8, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.automationallowed</code>.
     */
    public Boolean getAutomationallowed() {
        return (Boolean) get(8);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.lastseen</code>.
     */
    public void setLastseen(Long value) {
        set(9, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.lastseen</code>.
     */
    public Long getLastseen() {
        return (Long) get(9);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.expiration</code>.
     */
    public void setExpiration(Long value) {
        set(10, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.expiration</code>.
     */
    public Long getExpiration() {
        return (Long) get(10);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.row_hmac</code>.
     */
    public void setRowHmac(String value) {
        set(11, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.row_hmac</code>.
     */
    public String getRowHmac() {
        return (String) get(11);
    }

    /**
     * Setter for <code>keywhizdb_test.clients.spiffe_id</code>.
     */
    public void setSpiffeId(String value) {
        set(12, value);
    }

    /**
     * Getter for <code>keywhizdb_test.clients.spiffe_id</code>.
     */
    public String getSpiffeId() {
        return (String) get(12);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record13 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row13<Long, String, Long, Long, String, String, String, Boolean, Boolean, Long, Long, String, String> fieldsRow() {
        return (Row13) super.fieldsRow();
    }

    @Override
    public Row13<Long, String, Long, Long, String, String, String, Boolean, Boolean, Long, Long, String, String> valuesRow() {
        return (Row13) super.valuesRow();
    }

    @Override
    public Field<Long> field1() {
        return Clients.CLIENTS.ID;
    }

    @Override
    public Field<String> field2() {
        return Clients.CLIENTS.NAME;
    }

    @Override
    public Field<Long> field3() {
        return Clients.CLIENTS.UPDATEDAT;
    }

    @Override
    public Field<Long> field4() {
        return Clients.CLIENTS.CREATEDAT;
    }

    @Override
    public Field<String> field5() {
        return Clients.CLIENTS.DESCRIPTION;
    }

    @Override
    public Field<String> field6() {
        return Clients.CLIENTS.CREATEDBY;
    }

    @Override
    public Field<String> field7() {
        return Clients.CLIENTS.UPDATEDBY;
    }

    @Override
    public Field<Boolean> field8() {
        return Clients.CLIENTS.ENABLED;
    }

    @Override
    public Field<Boolean> field9() {
        return Clients.CLIENTS.AUTOMATIONALLOWED;
    }

    @Override
    public Field<Long> field10() {
        return Clients.CLIENTS.LASTSEEN;
    }

    @Override
    public Field<Long> field11() {
        return Clients.CLIENTS.EXPIRATION;
    }

    @Override
    public Field<String> field12() {
        return Clients.CLIENTS.ROW_HMAC;
    }

    @Override
    public Field<String> field13() {
        return Clients.CLIENTS.SPIFFE_ID;
    }

    @Override
    public Long component1() {
        return getId();
    }

    @Override
    public String component2() {
        return getName();
    }

    @Override
    public Long component3() {
        return getUpdatedat();
    }

    @Override
    public Long component4() {
        return getCreatedat();
    }

    @Override
    public String component5() {
        return getDescription();
    }

    @Override
    public String component6() {
        return getCreatedby();
    }

    @Override
    public String component7() {
        return getUpdatedby();
    }

    @Override
    public Boolean component8() {
        return getEnabled();
    }

    @Override
    public Boolean component9() {
        return getAutomationallowed();
    }

    @Override
    public Long component10() {
        return getLastseen();
    }

    @Override
    public Long component11() {
        return getExpiration();
    }

    @Override
    public String component12() {
        return getRowHmac();
    }

    @Override
    public String component13() {
        return getSpiffeId();
    }

    @Override
    public Long value1() {
        return getId();
    }

    @Override
    public String value2() {
        return getName();
    }

    @Override
    public Long value3() {
        return getUpdatedat();
    }

    @Override
    public Long value4() {
        return getCreatedat();
    }

    @Override
    public String value5() {
        return getDescription();
    }

    @Override
    public String value6() {
        return getCreatedby();
    }

    @Override
    public String value7() {
        return getUpdatedby();
    }

    @Override
    public Boolean value8() {
        return getEnabled();
    }

    @Override
    public Boolean value9() {
        return getAutomationallowed();
    }

    @Override
    public Long value10() {
        return getLastseen();
    }

    @Override
    public Long value11() {
        return getExpiration();
    }

    @Override
    public String value12() {
        return getRowHmac();
    }

    @Override
    public String value13() {
        return getSpiffeId();
    }

    @Override
    public ClientsRecord value1(Long value) {
        setId(value);
        return this;
    }

    @Override
    public ClientsRecord value2(String value) {
        setName(value);
        return this;
    }

    @Override
    public ClientsRecord value3(Long value) {
        setUpdatedat(value);
        return this;
    }

    @Override
    public ClientsRecord value4(Long value) {
        setCreatedat(value);
        return this;
    }

    @Override
    public ClientsRecord value5(String value) {
        setDescription(value);
        return this;
    }

    @Override
    public ClientsRecord value6(String value) {
        setCreatedby(value);
        return this;
    }

    @Override
    public ClientsRecord value7(String value) {
        setUpdatedby(value);
        return this;
    }

    @Override
    public ClientsRecord value8(Boolean value) {
        setEnabled(value);
        return this;
    }

    @Override
    public ClientsRecord value9(Boolean value) {
        setAutomationallowed(value);
        return this;
    }

    @Override
    public ClientsRecord value10(Long value) {
        setLastseen(value);
        return this;
    }

    @Override
    public ClientsRecord value11(Long value) {
        setExpiration(value);
        return this;
    }

    @Override
    public ClientsRecord value12(String value) {
        setRowHmac(value);
        return this;
    }

    @Override
    public ClientsRecord value13(String value) {
        setSpiffeId(value);
        return this;
    }

    @Override
    public ClientsRecord values(Long value1, String value2, Long value3, Long value4, String value5, String value6, String value7, Boolean value8, Boolean value9, Long value10, Long value11, String value12, String value13) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        value9(value9);
        value10(value10);
        value11(value11);
        value12(value12);
        value13(value13);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached ClientsRecord
     */
    public ClientsRecord() {
        super(Clients.CLIENTS);
    }

    /**
     * Create a detached, initialised ClientsRecord
     */
    public ClientsRecord(Long id, String name, Long updatedat, Long createdat, String description, String createdby, String updatedby, Boolean enabled, Boolean automationallowed, Long lastseen, Long expiration, String rowHmac, String spiffeId) {
        super(Clients.CLIENTS);

        setId(id);
        setName(name);
        setUpdatedat(updatedat);
        setCreatedat(createdat);
        setDescription(description);
        setCreatedby(createdby);
        setUpdatedby(updatedby);
        setEnabled(enabled);
        setAutomationallowed(automationallowed);
        setLastseen(lastseen);
        setExpiration(expiration);
        setRowHmac(rowHmac);
        setSpiffeId(spiffeId);
    }
}