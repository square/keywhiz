package keywhiz.service.permissions;

/**
 * Action class defines the set of actions that can be performed on an object
 * */
public final class Action {
  private Action() {}

  public static final String ADD = "ADD";

  public static final String CREATE = "CREATE";

  public static final String DELETE = "DELETE";

  public static final String READ = "READ";

  public static final String REMOVE = "REMOVE";

  public static final String UPDATE = "UPDATE";
}
