/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ja.JapaneseTokenizer.Mode;
import org.apache.lucene.analysis.ja.dict.UserDictionary;
import org.apache.lucene.analysis.ja.tokenattributes.BaseFormAttribute;
import org.apache.lucene.analysis.ja.tokenattributes.InflectionAttribute;
import org.apache.lucene.analysis.ja.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.ja.tokenattributes.ReadingAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Azure Functions with HTTP Trigger.
 */
public class CustomAnalyzerFunction {
	public static UserDictionary readDict() {

		try (InputStream stream = CustomAnalyzerFunction.class.getResourceAsStream("userdict.txt");
				Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return UserDictionary.open(reader);
		} catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	public static String analyzeQuery(String query) {
		// String query = "えーと、私はﾏｲｸﾛｿﾌﾄのＡｚｕｒｅコンテナーを使おうと思っていた。";
		Analyzer custom = new CustomJapaneseAnalyzer(readDict(), Mode.SEARCH,
				CustomJapaneseAnalyzer.getDefaultStopSet(), CustomJapaneseAnalyzer.getDefaultStopTags());

		TokenStream tokenStream = custom.tokenStream("", new StringReader(query));
		CharTermAttribute term = tokenStream.addAttribute(CharTermAttribute.class);
		// OffsetAttribute offset = tokenStream.addAttribute(OffsetAttribute.class);
		// PartOfSpeechAttribute partOfSpeech = tokenStream.addAttribute(PartOfSpeechAttribute.class);
		// InflectionAttribute inflection = tokenStream.addAttribute(InflectionAttribute.class);
		// BaseFormAttribute baseForm = tokenStream.addAttribute(BaseFormAttribute.class);
		// ReadingAttribute reading = tokenStream.addAttribute(ReadingAttribute.class);

		try {
			tokenStream.reset();
			StringBuilder output = new StringBuilder();

			while (tokenStream.incrementToken()) {
				output.append(term.toString() + " ");
				/* 参考 https://www.mlab.im.dendai.ac.jp/~yamada/ir/MorphologicalAnalyzer/Lucene_Kuromoji.html
				 * System.out.println(term.toString() + "\t" // 表層形
				 * + offset.startOffset() + "-" + offset.endOffset() + "," // 文字列中の位置
				 * + partOfSpeech.getPartOfSpeech() + "," // 品詞-品詞細分類1-品詞細分類2
				 * + inflection.getInflectionType() + "," // 活用型
				 * + inflection.getInflectionForm() + "," // 活用形
				 * + baseForm.getBaseForm() + "," // 原形 (活用しない語では null)
				 * + reading.getReading() + "," // 読み
				 * + reading.getPronunciation()); // 発音
				 */
			}

			tokenStream.close();
			return output.toString();
		} catch (IOException e1) {
			e1.printStackTrace();
			return "error IOException";
		}
	}

	/**
	 * This function listens at endpoint "/api/Analyze". Two ways to invoke it using
	 * "curl" command in bash:
	 * 1. curl -d "HTTP Body" {your host}/api/Analyze
	 * 
	 */
	@FunctionName("Analyze")
	public HttpResponseMessage run(
			@HttpTrigger(name = "req", methods = { HttpMethod.POST },
			 authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {
		context.getLogger().info("Java HTTP trigger processed a request.");

		// Parse query parameter
		final String body = request.getBody().orElse(null);

		if (body == null) {
			return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
					.body("Please pass a name on the query string or in the request body").build();
		} else {
			
			try {
				JSONObject obj = new JSONObject(body);
				JSONArray arrValues = obj.getJSONArray("values");
				JSONArray arrOutputValues = new JSONArray();

				for (int i = 0; i < arrValues.length(); i++) {
					String recordId = arrValues.getJSONObject(i).getString("recordId");
					String text = arrValues.getJSONObject(i).getJSONObject("data").getString("text");
					arrOutputValues.put(makeRes(recordId, analyzeQuery(text)));
				}

				JSONObject returnJsonObj = new JSONObject();
				returnJsonObj.put("values", arrOutputValues);

				return request.createResponseBuilder(HttpStatus.OK)
				.header("Content-Type", "application/json")
				.body(returnJsonObj.toString())
				.build();

			} catch (JSONException e) {
				return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
						.body(e.getMessage()).build();
			}
		}
	}

	public static JSONObject makeRes(String recordId, String text) throws JSONException {
		JSONObject jsonObj = new JSONObject();
		jsonObj.put("recordId", recordId);
		jsonObj.put("words", text);
		return jsonObj;
	}

}
