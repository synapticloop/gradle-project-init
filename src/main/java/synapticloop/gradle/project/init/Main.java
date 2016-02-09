package synapticloop.gradle.project.init;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import synapticloop.gradle.project.init.bean.KeyValueBean;
import synapticloop.templar.Parser;
import synapticloop.templar.exception.ParseException;
import synapticloop.templar.exception.RenderException;
import synapticloop.templar.utils.TemplarContext;
import synapticloop.util.SimpleLogger;
import synapticloop.util.SimpleUsage;

public class Main {
	private static final SimpleLogger LOGGER = SimpleLogger.getLogger("MAIN");
	private static Set<String> TRUE_ANSWERS = new HashSet<String>();
	static {
		TRUE_ANSWERS.add("YES");
		TRUE_ANSWERS.add("TRUE");
		TRUE_ANSWERS.add("1");
		TRUE_ANSWERS.add("Y");
	}

	private static final String SYNAPTICLOOP_QUESTIONS = "https://raw.githubusercontent.com/synapticloop/gradle-project-init/master/build.gradle.";
	public static void main(String[] args) {
		if(args.length != 1) {
			SimpleUsage.usageAndExit("Invalid arguments.");
		}

		QuestionAndAnswerManager questionAndAnswerManager = null;
		String location = args[0];
		try {

			String questionsLocation = SYNAPTICLOOP_QUESTIONS + location + ".questions";
			LOGGER.info("Loading questions for '" + location + "' from '" + questionsLocation + "'.");
			String questions = new String(IOUtils.toByteArray(new URL(questionsLocation)));
			questionAndAnswerManager = new QuestionAndAnswerManager(questions);
		} catch (IOException ex) {
			// TODO Auto-generated catch block
			ex.printStackTrace();
			SimpleUsage.usageAndExit("Could not retrieve the questions.");
		}


		// now run through the questions

		askStringQuestions(questionAndAnswerManager);

		askChoiceQuestions(questionAndAnswerManager);

		askBooleanQuestions(questionAndAnswerManager);

		// now we are ready to generate
		generate(questionAndAnswerManager);
	}

	@SuppressWarnings("unchecked")
	private static void generate(QuestionAndAnswerManager questionAndAnswerManager) {
		TemplarContext templarContext = questionAndAnswerManager.getTemplarContext();
		Parser parser = null;

		try {
			parser = new Parser(new String(IOUtils.toByteArray(new URL(questionAndAnswerManager.getMetaBuildDotGradleTemplate()))));

			String rendered = parser.render(templarContext);

			File buildDotGradle = new File("./build.gradle");
			if(buildDotGradle.exists()) {
				long currentTimeMillis = System.currentTimeMillis();
				LOGGER.warn("build.gradle file already exists, moving it out of the way to build.gradle."+ currentTimeMillis + ".bak");
				buildDotGradle.renameTo(new File("build.gradle." + currentTimeMillis + ".bak"));
			}
			buildDotGradle = new File("./build.gradle");
			FileUtils.write(buildDotGradle, rendered);
		} catch (ParseException | RenderException | IOException ex) {
			LOGGER.fatal("Could not retrieve or parse the build.gradle template", ex);
		}

		try {
			parser = new Parser(new String(IOUtils.toByteArray(new URL(questionAndAnswerManager.getMetaSettingsDotGradleTemplate()))));

			String rendered = parser.render(templarContext);

			File settingsDotGradle = new File("./settings.gradle");
			if(settingsDotGradle.exists()) {
				long currentTimeMillis = System.currentTimeMillis();
				LOGGER.warn("settings.gradle file already exists, moving it out of the way to settings.gradle."+ currentTimeMillis + ".bak");
				settingsDotGradle.renameTo(new File("settings.gradle." + currentTimeMillis + ".bak"));
			}
			settingsDotGradle = new File("./settings.gradle");
			FileUtils.write(settingsDotGradle, rendered);
		} catch (ParseException | RenderException | IOException ex) {
			LOGGER.fatal("Could not retrieve or parse the settings.gradle template", ex);
		}

		LOGGER.info("Creating directories...");
		List<KeyValueBean> createDirectoryList = (List<KeyValueBean>)templarContext.get("createDirectoryList");
		if(null != createDirectoryList) {
			for (KeyValueBean keyValueBean : createDirectoryList) {
				String createDirectory = (String) keyValueBean.getKey();
				if(new File(createDirectory).mkdirs()) {
					LOGGER.info("Successfully created directory '" + createDirectory + "'.");
				}
			}
		}

		LOGGER.info("Generating .gitignore");
		List<KeyValueBean> gitignoreList = (List<KeyValueBean>)templarContext.get("gitignoreList");
		if(null != gitignoreList) {
			StringBuilder stringBuilder = new StringBuilder();
			for (KeyValueBean keyValueBean : gitignoreList) {
				stringBuilder.append(keyValueBean.getKey());
				stringBuilder.append(",");
			}
			try {
				FileUtils.write(new File(".gitignore"), IOUtils.toString(new URL("https://www.gitignore.io/api/" + stringBuilder.toString())));
			} catch (IOException ex) {
				LOGGER.fatal("Could not generate the .gitignore file", ex);
			}
		}
	}

	private static void askStringQuestions(QuestionAndAnswerManager questionAndAnswerManager) {
		Map<String, List<KeyValueBean>> stringQuestions = questionAndAnswerManager.getStringQuestions();

		Set<String> stringKeySet = stringQuestions.keySet();
		List<String> stringAsSortedList = Util.asSortedList(stringKeySet);
		for (String group : stringAsSortedList) {
			System.out.println("\nAsking string questions for group [ " + group + " ]:");
			List<KeyValueBean> stringQuestionList = stringQuestions.get(group);

			for (KeyValueBean keyValueBean : stringQuestionList) {

				Object defaultValue = keyValueBean.getDefaultValue();

				System.out.print("    [ " + group + " ] " + 
						keyValueBean.getKey() + 
						(defaultValue != null ? " [" + defaultValue + "]" : "") + 
						" ?: ");

				String response = getResponse();

				if(null == response || response.trim().length() == 0) {
					keyValueBean.setValue(defaultValue);
				} else {
					keyValueBean.setValue(response);
				}
				questionAndAnswerManager.setContext(group, keyValueBean);
			}
		}
	}

	private static void askChoiceQuestions(QuestionAndAnswerManager questionAndAnswerManager) {
		Map<String, List<KeyValueBean>> choiceQuestions = questionAndAnswerManager.getChoiceQuestions();
		Iterator<String> choiceQuestionsIterator = choiceQuestions.keySet().iterator();
		while (choiceQuestionsIterator.hasNext()) {
			String group = (String) choiceQuestionsIterator.next();
			System.out.println("Choose one of the following for '" + group + "' [default]:");
			int i = 1;

			for(KeyValueBean keyValueBean : choiceQuestions.get(group)) {
				System.out.println("      " + i + ") " + 
						(keyValueBean.getHasDefault() ? "[" : "") + 
						keyValueBean.getValue() + 
						(keyValueBean.getHasDefault() ? "]" : ""));
				i++;
			}

			System.out.print("    [ " + group + " ] ?: ");

			saveChoiceResponse(questionAndAnswerManager, choiceQuestions.get(group), group);
		}
	}

	private static void saveChoiceResponse(QuestionAndAnswerManager questionAndAnswerManager, List<KeyValueBean> questionList, String groupAndKey) {
		String[] split = groupAndKey.split("\\.");
		String group = split[0];

		while(true) {
			String response = getResponse();
			if(null == response || response.trim().length() == 0) {
				// see if we have a default
				for (KeyValueBean keyValueBean : questionList) {
					if(keyValueBean.getHasDefault()) {
						System.out.println("Nothing entered, using default value of '" + keyValueBean.getValue() + "'.");
						questionAndAnswerManager.setContext(group, keyValueBean);
						return;
					}
				}
				System.out.print("No default value set, please choose a value: ");
			} else {
				// see if we can parse it
				try {
					int intChoice = Integer.parseInt(response);
					if(intChoice == 0 || intChoice > questionList.size()) {
						System.out.print("Invalid choice, please enter a number between 1 and " + questionList.size() + ": ");
					} else {
						KeyValueBean selectedChoice = questionList.get(intChoice -1);
						System.out.println("Choice of '" + selectedChoice + "' selected.");
						questionAndAnswerManager.setContext(group, selectedChoice);
						return;
					}
				} catch(NumberFormatException ex) {
					System.out.println("Invalid integer choice for input '" + response + "', try again: ");
				}
			}
		}
	}


	private static void askBooleanQuestions(QuestionAndAnswerManager questionAndAnswerManager) {
		Map<String, List<KeyValueBean>> booleanQuestions = questionAndAnswerManager.getBooleanQuestions();
		Set<String> keySet = booleanQuestions.keySet();
		List<String> asSortedList = Util.asSortedList(keySet);
		for (String group : asSortedList) {
			System.out.println("\nAsking boolean questions for group [ " + group + " ]:");
			List<KeyValueBean> booleanQuestionList = booleanQuestions.get(group);

			for (KeyValueBean keyValueBean : booleanQuestionList) {
				System.out.print("    [ " + group + " ] " + 
						keyValueBean.getKey() + 
						(null != keyValueBean.getValue() ? " : " + keyValueBean.getValue() : "")  + 
						" " + 
						"[" + 
						keyValueBean.getDefaultValue() +
						"] ? :");

				String response = getResponse();
				if(null == response || response.trim().length() ==0) {
					if((Boolean)keyValueBean.getDefaultValue()) {
						questionAndAnswerManager.setContext(group, keyValueBean);
					}
				} else {
					if(TRUE_ANSWERS.contains(response.toUpperCase())) {
						questionAndAnswerManager.setContext(group, keyValueBean);
					}
				}
			}
		}
	}

	private static String getResponse() {
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		try {
			return(br.readLine());
		} catch (IOException ex) {
		}
		return(null);
	}

}
