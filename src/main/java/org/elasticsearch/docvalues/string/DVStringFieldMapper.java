/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.docvalues.string;

import static org.elasticsearch.index.mapper.core.TypeParsers.parseField;
import static org.elasticsearch.index.mapper.core.TypeParsers.parseMultiField;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.index.codec.docvaluesformat.DocValuesFormatProvider;
import org.elasticsearch.index.codec.postingsformat.PostingsFormatProvider;
import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.mapper.core.AbstractFieldMapper;
import org.elasticsearch.index.mapper.core.StringFieldMapper;
import org.elasticsearch.index.similarity.SimilarityProvider;

/**
 * Similar to a StringFieldMapper but the string is tokenized and the first token
 * of the token stream is stored as a doc value.
 */
public class DVStringFieldMapper extends StringFieldMapper {
    
    private ESLogger logger = ESLoggerFactory.getLogger(DVStringFieldMapper.class.getName());

    public static final String CONTENT_TYPE = "dvstring";

	private static final NamedAnalyzer DEFAULT_LWC_ANALYZER = new NamedAnalyzer("dvlwc", new Analyzer() {
		@Override
		protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
          Tokenizer tokenizer = new KeywordTokenizer(reader);//new StandardTokenizer(reader);
//          TokenFilter firstFilter = new LimitTokenCountFilter(tokenizer, 1, false);
          TokenFilter filter = new LowerCaseFilter(tokenizer);
          return new TokenStreamComponents(tokenizer, filter);
		}
	});

	public static class TypeParser implements Mapper.TypeParser {
		public Builder parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            Builder builder = new Builder(name);
            parseField(builder, name, node, parserContext);
            for (Map.Entry<String, Object> entry : node.entrySet()) {
                String propName = Strings.toUnderscoreCase(entry.getKey());
                Object propNode = entry.getValue();
                if (propName.equals("null_value")) {
                    builder.nullValue(propNode.toString());
                } else if (propName.equals("search_quote_analyzer")) {
                    NamedAnalyzer analyzer = parserContext.analysisService().analyzer(propNode.toString());
                    if (analyzer == null) {
                        throw new MapperParsingException("Analyzer [" + propNode.toString() + "] not found for field [" + name + "]");
                    }
                    builder.searchQuotedAnalyzer(analyzer);
                } else if (propName.equals("index_docvalues_analyzer")) {
                    NamedAnalyzer analyzer = parserContext.analysisService().analyzer(propNode.toString());
                    if (analyzer == null) {
                        throw new MapperParsingException("Analyzer [" + propNode.toString() + "] not found for field [" + name + "]");
                    }
                    builder.docValuesAnalyzer = analyzer;
                // due to protected fields... it sucks to support this.nevermind for now.
                /*} else if (propName.equals("position_offset_gap")) {
                    builder.positionOffsetGap(XContentMapValues.nodeIntegerValue(propNode, -1));
                    // we need to update to actual analyzers if they are not set in this case...
                    // so we can inject the position offset gap...
                    if (builder.indexAnalyzer == null) {
                        builder.indexAnalyzer = parserContext.analysisService().defaultIndexAnalyzer();
                    }
                    if (builder.searchAnalyzer == null) {
                        builder.searchAnalyzer = parserContext.analysisService().defaultSearchAnalyzer();
                    }
                    if (builder.searchQuotedAnalyzer == null) {
                        builder.searchQuotedAnalyzer = parserContext.analysisService().defaultSearchQuoteAnalyzer();
                    }*/
                } else if (propName.equals("ignore_above")) {
                    builder.ignoreAbove(XContentMapValues.nodeIntegerValue(propNode, -1));
                } else {
                    parseMultiField(builder, name, parserContext, propName, propNode);
                }
            }
            return builder;
		}
	}

    public static class Builder extends AbstractFieldMapper.Builder<Builder, DVStringFieldMapper> {

        protected String nullValue = Defaults.NULL_VALUE;

        protected int positionOffsetGap = Defaults.POSITION_OFFSET_GAP;

        protected NamedAnalyzer searchQuotedAnalyzer;

        protected int ignoreAbove = Defaults.IGNORE_ABOVE;
        
        protected NamedAnalyzer docValuesAnalyzer;

        public Builder(String name) {
            super(name, new FieldType(Defaults.FIELD_TYPE));
            builder = this;
        }

        public Builder nullValue(String nullValue) {
            this.nullValue = nullValue;
            return this;
        }

        @Override
        public Builder searchAnalyzer(NamedAnalyzer searchAnalyzer) {
            super.searchAnalyzer(searchAnalyzer);
            if (searchQuotedAnalyzer == null) {
                searchQuotedAnalyzer = searchAnalyzer;
            }
            return this;
        }

        public Builder positionOffsetGap(int positionOffsetGap) {
            this.positionOffsetGap = positionOffsetGap;
            return this;
        }

        public Builder searchQuotedAnalyzer(NamedAnalyzer analyzer) {
            this.searchQuotedAnalyzer = analyzer;
            return builder;
        }

        public Builder ignoreAbove(int ignoreAbove) {
            this.ignoreAbove = ignoreAbove;
            return this;
        }
        
        @Override
        public DVStringFieldMapper build(BuilderContext context) {
        	if (docValuesAnalyzer == null) {
        		docValuesAnalyzer = DEFAULT_LWC_ANALYZER;
        	}
            if (positionOffsetGap > 0) {
                // we need to update to actual analyzers if they are not set in this case...
                // so we can inject the position offset gap...
                indexAnalyzer = new NamedAnalyzer(indexAnalyzer, positionOffsetGap);
                searchAnalyzer = new NamedAnalyzer(searchAnalyzer, positionOffsetGap);
                searchQuotedAnalyzer = new NamedAnalyzer(searchQuotedAnalyzer, positionOffsetGap);
                docValuesAnalyzer = new NamedAnalyzer(docValuesAnalyzer, positionOffsetGap);
            }
            // if the field is not analyzed, then by default, we should omit norms and have docs only
            // index options, as probably what the user really wants
            // if they are set explicitly, we will use those values
            // we also change the values on the default field type so that toXContent emits what
            // differs from the defaults
            FieldType defaultFieldType = new FieldType(Defaults.FIELD_TYPE);
            if (fieldType.indexed() && !fieldType.tokenized()) {
                defaultFieldType.setOmitNorms(true);
                defaultFieldType.setIndexOptions(IndexOptions.DOCS_ONLY);
                if (!omitNormsSet && boost == Defaults.BOOST) {
                    fieldType.setOmitNorms(true);
                }
                if (!indexOptionsSet) {
                    fieldType.setIndexOptions(IndexOptions.DOCS_ONLY);
                }
            }
            defaultFieldType.freeze();
            DVStringFieldMapper fieldMapper = new DVStringFieldMapper(buildNames(context),
                    boost, fieldType, defaultFieldType, Boolean.FALSE/*docValues*/, nullValue,
                    indexAnalyzer, searchAnalyzer, searchQuotedAnalyzer, docValuesAnalyzer,
                    positionOffsetGap, ignoreAbove, postingsProvider, docValuesProvider, similarity, normsLoading, 
                    fieldDataSettings, context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo);
            fieldMapper.includeInAll(includeInAll);
            return fieldMapper;
        }
    }
    
    private boolean hasDocValsNow = false;
    private final NamedAnalyzer docValuesAnalyzer;
    
	protected DVStringFieldMapper(
			org.elasticsearch.index.mapper.FieldMapper.Names names,
			float boost,
			FieldType fieldType,
			FieldType defaultFieldType,
			Boolean docValues,
			String nullValue,
			NamedAnalyzer indexAnalyzer,
			NamedAnalyzer searchAnalyzer,
			NamedAnalyzer searchQuotedAnalyzer,
			NamedAnalyzer docValuesAnalyzer,
			int positionOffsetGap,
			int ignoreAbove,
			PostingsFormatProvider postingsFormat,
			DocValuesFormatProvider docValuesFormat,
			SimilarityProvider similarity,
			org.elasticsearch.index.mapper.FieldMapper.Loading normsLoading,
			Settings fieldDataSettings,
			Settings indexSettings,
			org.elasticsearch.index.mapper.core.AbstractFieldMapper.MultiFields multiFields,
			org.elasticsearch.index.mapper.core.AbstractFieldMapper.CopyTo copyTo) {
		super(names, boost, fieldType, defaultFieldType, docValues, nullValue,
				indexAnalyzer, searchAnalyzer, searchQuotedAnalyzer, positionOffsetGap,
				ignoreAbove, postingsFormat, docValuesFormat, similarity, normsLoading,
				fieldDataSettings, indexSettings, multiFields, copyTo);
		this.docValuesAnalyzer = docValuesAnalyzer;
		this.hasDocValsNow = true;
	}
	
    @Override
	public boolean hasDocValues() {
		return hasDocValsNow;
	}
    
	@Override
    protected void parseCreateField(ParseContext context, List<Field> fields) throws IOException {
		// luckily this is single thread access and we dont need a thread local.
		hasDocValsNow = false;
        super.parseCreateField(context, fields);
        hasDocValsNow = true;
        String value = null;
        if (context.externalValueSet()) {
        	value = (String) context.externalValue();
        } else {
        	for (Field f : fields) {
        		Class<?> fClass = f.getClass();
        		if (fClass == Field.class || fClass == TextField.class || fClass == StringField.class) {
        			value = f.stringValue();
        			break;
        		}
        	}
        }
        if (value != null) {
        	TokenStream stream = docValuesAnalyzer.analyzer().tokenStream(null, new StringReader(value));
        	CharTermAttribute cattr = stream.addAttribute(CharTermAttribute.class);
        	stream.reset();
        	while (stream.incrementToken()) {
        		String token = cattr.toString();
        		// take the first token and make it a doc value
        		fields.add(new SortedSetDocValuesField(names.indexName(), new BytesRef(token)));
        		break;
        	}
        	stream.end();
        	stream.close();
        }
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }

}
