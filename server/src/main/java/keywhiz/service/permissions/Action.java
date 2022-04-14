package keywhiz.service.permissions;

/**
 * Action class defines the set of actions that can be performed on an object
 * */
public final class Action {
  private Action() {}

  // add clients to groups or add secrets to groups
  public static final String ADD = "ADD";

  public static final String CREATE = "CREATE";

  // for secrets and permissions, it will be a soft deletion
  public static final String DELETE = "DELETE";

  public static final String READ = "READ";

  // remove clients to groups or remove secrets to groups
  public static final String REMOVE = "REMOVE";

  public static final String UPDATE = "UPDATE";
}
