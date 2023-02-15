package keywhiz.model;

public interface SecretsOrDeletedSecretsRecord extends org.jooq.Record {
  Long getId();
  String getName();
  Long getUpdatedat();
  Long getCreatedat();
  String getDescription();
  String getCreatedby();
  String getUpdatedby();
  String getType();
  String getOptions();
  Long getCurrent();
  String getRowHmac();
  Long getOwner();
  Long getExpiry();
}
