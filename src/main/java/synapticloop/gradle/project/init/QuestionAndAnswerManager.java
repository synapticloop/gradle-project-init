package synapticloop.gradle.project.init;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import synapticloop.gradle.project.init.bean.KeyValueBean;
import synapticloop.templar.utils.TemplarConfiguration;
import synapticloop.templar.utils.TemplarContext;
import synapticloop.util.SimpleLogger;

public class QuestionAndAnswerManager {
	private static final String PROPERTY_BOOLEAN_DOT = "boolean.";
	private static final String PROPERTY_STRING_DOT = "string.";
	private static final String PROPERTY_CHOICE_DOT = "choice.";

	private static final String META_BUILD_DOT_GRADLE_TEMPLATE = "meta.build.gradle.template";
	private static final String META_SETTINGS_DOT_GRADLE_TEMPLATE = "meta.settings.gradle.template";

	private static final SimpleLogger LOGGER = SimpleLogger.getLoggerSimpleName(QuestionAndAnswerManager.class);

	private final Map<String, List<KeyValueBean>> BOOLEAN_QUESTIONS = new ConcurrentHashMap<String, List<KeyValueBean>>();
	private final Map<String, List<KeyValueBean>> STRING_QUESTIONS = new ConcurrentHashMap<String, List<KeyValueBean>>();
	private final Map<String, List<KeyValueBean>> CHOICE_QUESTIONS = new ConcurrentHashMap<String, List<KeyValueBean>>();

	private final Map<String, Map<String, KeyValueBean>> lookupContext = new HashMap<String, Map<String, KeyValueBean>>();
	private final Map<String, List<KeyValueBean>> listLookupContext = new HashMap<String, List<KeyValueBean>>();

	private static Map<String, String> propertyMap = new LinkedHashMap<String, String>();

	private String metaBuildDotGradleTemplate = null;
	private String metaSettingsDotGradleTemplate = null;

	private TemplarConfiguration templarConfiguration = new TemplarConfiguration(true, true, true);
	private TemplarContext templarContext = new TemplarContext(templarConfiguration);

	public QuestionAndAnswerManager(String questions) {
		LOGGER.info("Parsing questions");

		BufferedReader bufferedReader = null;
		bufferedReader = new BufferedReader(new StringReader(questions));
		String line = null;
		try {
			while((line = bufferedReader.readLine()) != null) {
				String trimmedLine = line.trim();
				if(trimmedLine.length() != 0 && !trimmedLine.startsWith("#")) {
					String[] splits = line.split("=", 2);
					if(splits.length != 2) {
						LOGGER.fatal("Could not parse the question line '" + line + "', ignoring");
					} else {
						propertyMap.put(splits[0], splits[1]);
					}
				}
			}
		} catch(IOException ex) {
			LOGGER.fatal("Could not parse the questions");
		}

		parseMeta();

		// parse all of the string questions
		parseStringQuestions();

		// parse all of the choice questions
		parseChoiceQuestions();

		// now go through and build up the structures
		parseBooleanQuestions();
	}

	private void parseMeta() {
		this.metaBuildDotGradleTemplate = propertyMap.get(META_BUILD_DOT_GRADLE_TEMPLATE);
		if(null == metaBuildDotGradleTemplate) {
			LOGGER.fatal("Could not find the property '" + META_BUILD_DOT_GRADLE_TEMPLATE + "', which is required.");
			throw new RuntimeException("Cannot continue without property '" + META_BUILD_DOT_GRADLE_TEMPLATE + "'.");
		}

		this.metaSettingsDotGradleTemplate = propertyMap.get(META_SETTINGS_DOT_GRADLE_TEMPLATE);
		if(null == metaSettingsDotGradleTemplate) {
			LOGGER.fatal("Could not find the property '" + META_SETTINGS_DOT_GRADLE_TEMPLATE + "', which is required.");
			throw new RuntimeException("Cannot continue without property '" + META_SETTINGS_DOT_GRADLE_TEMPLATE + "'.");
		}

	}

	private void parseStringQuestions() {
		Iterator<String> iterator = propertyMap.keySet().iterator();
		while (iterator.hasNext()) {
			String propertyKey = iterator.next();
			if(propertyKey.startsWith(PROPERTY_STRING_DOT)) {
				LOGGER.debug("Found string property of '" + propertyKey + "'.");
				String substringProperty = propertyKey.substring(PROPERTY_STRING_DOT.length());

				String[] valueSplits = substringProperty.split("\\.", 2);
				String group = valueSplits[0];
				String keyValueKey = valueSplits[1];

				List<KeyValueBean> keyValueBeans = null;

				if(STRING_QUESTIONS.containsKey(group)) {
					keyValueBeans = STRING_QUESTIONS.get(group);
				} else {
					keyValueBeans = new ArrayList<KeyValueBean>();
				}

				keyValueBeans.add(new KeyValueBean(keyValueKey, null, propertyMap.get(propertyKey)));
				STRING_QUESTIONS.put(group, keyValueBeans);
			}
		}
	}

	/**
	 * Go through and parse the choice questions
	 */
	private void parseChoiceQuestions() {
		Iterator<String> iterator = propertyMap.keySet().iterator();
		while (iterator.hasNext()) {
			String key = iterator.next();
			if(key.startsWith(PROPERTY_CHOICE_DOT)) {
				LOGGER.debug("Found 'choice' property of '" + key + "'.");

				String substringProperty = key.substring(PROPERTY_CHOICE_DOT.length());
				String[] valueSplits = substringProperty.split("\\.", 2);
				String group = valueSplits[0];
				String keyValueKey = valueSplits[1];

				List<KeyValueBean> keyValueBeans = CHOICE_QUESTIONS.get(group);

				if(null == keyValueBeans) {
					keyValueBeans = new ArrayList<KeyValueBean>();
				}

				String[] choicesArray = propertyMap.get(key).split(",");

				for (String choice : choicesArray) {
					String trimmedChoice = choice.trim();
					String defaultValue = null;
					if(trimmedChoice.startsWith("[") && trimmedChoice.endsWith("]")) {
						defaultValue = trimmedChoice.substring(1, trimmedChoice.length() -1);
						trimmedChoice = defaultValue;
					}
					keyValueBeans.add(new KeyValueBean(keyValueKey, trimmedChoice, defaultValue));
				}

				CHOICE_QUESTIONS.put(substringProperty, keyValueBeans);
			}
		}
	}

	private void parseBooleanQuestions() {
		Iterator<String> iterator = propertyMap.keySet().iterator();
		while (iterator.hasNext()) {
			String key = iterator.next();
			if(key.startsWith(PROPERTY_BOOLEAN_DOT)) {
				LOGGER.debug("Found boolean property of '" + key + "'.");
				String value = key.substring(PROPERTY_BOOLEAN_DOT.length());
				String[] valueSplits = value.split("\\.", 2);
				String group = valueSplits[0];
				String keyValueKey = valueSplits[1];

				List<KeyValueBean> keyValueBeans = null;

				if(BOOLEAN_QUESTIONS.containsKey(group)) {
					keyValueBeans = BOOLEAN_QUESTIONS.get(group);
				} else {
					keyValueBeans = new ArrayList<KeyValueBean>();
				}

				String[] keyValueSplit = keyValueKey.split("\\|", 2);
				String beanKey = null;
				String beanValue = null;

				switch (keyValueSplit.length) {
				case 2:
					beanValue = keyValueSplit[1];
				case 1:
					beanKey = keyValueSplit[0];
					break;
				default:
					break;
				}

				keyValueBeans.add(new KeyValueBean(beanKey, beanValue, Boolean.valueOf(Boolean.parseBoolean(propertyMap.get(key)))));

				BOOLEAN_QUESTIONS.put(group, keyValueBeans);
			}
		}
	}

	public void setContext(String group, KeyValueBean keyValueBean) {
		String key = keyValueBean.getKey();

		Map<String, KeyValueBean> keyLookup = lookupContext.get(group);
		if(null == keyLookup) {
			keyLookup = new HashMap<String, KeyValueBean>();
		}

		keyLookup.put(key, keyValueBean);
		lookupContext.put(group, keyLookup);

		List<KeyValueBean> groupList = listLookupContext.get(group + "List");
		if(null == groupList) {
			groupList = new ArrayList<KeyValueBean>();
		}

		groupList.add(keyValueBean);
		listLookupContext.put(group + "List", groupList);
	}

	public Map<String, List<KeyValueBean>> getBooleanQuestions() { return BOOLEAN_QUESTIONS; }
	public Map<String, List<KeyValueBean>> getStringQuestions() { return STRING_QUESTIONS; }
	public Map<String, List<KeyValueBean>> getChoiceQuestions() { return CHOICE_QUESTIONS; }

	public TemplarContext getTemplarContext() {
		// go through the current context and generate the lists
		Iterator<String> lookupIterator = lookupContext.keySet().iterator();
		while (lookupIterator.hasNext()) {
			String key = (String) lookupIterator.next();
			templarContext.add(key, lookupContext.get(key));
		}

		Iterator<String> listLookupIterator = listLookupContext.keySet().iterator();
		while (listLookupIterator.hasNext()) {
			String key = (String) listLookupIterator.next();
			templarContext.add(key, listLookupContext.get(key));
		}
		return(templarContext);
	}

	public String getMetaBuildDotGradleTemplate() { return this.metaBuildDotGradleTemplate; }
	public String getMetaSettingsDotGradleTemplate() { return this.metaSettingsDotGradleTemplate; }
}
