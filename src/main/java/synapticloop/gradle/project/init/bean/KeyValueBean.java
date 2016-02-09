package synapticloop.gradle.project.init.bean;

public class KeyValueBean {
	private String key = null;
	private Object value = null;
	private Object defaultValue = null;

	public KeyValueBean(String key, Object value, Object defaultValue) {
		this.key = key;

		if(null != value && value instanceof String && ((String)value).trim().length() == 0) {
			this.value = null;
		} else {
			this.value = value;
		}

		// now for whether this is default
		if(null != defaultValue && defaultValue instanceof String && ((String)defaultValue).trim().length() == 0) {
			this.defaultValue = null;
		} else {
			this.defaultValue = defaultValue;
		}
	}

	public String getKey() { return this.key; }
	public Object getValue() { return this.value; }
	public void setValue(Object value) { this.value = value; }
	public Object getDefaultValue() { return this.defaultValue; }
	public boolean getHasDefault() { return(null != defaultValue); }

	@Override
	public String toString() {
		return "KeyValueBean [key=" + this.key + ", value=" + this.value + ", defaultValue=" + this.defaultValue + "]";
	}
}
