Experimental Doc Values String mapping type
===========================================

This plugin defines a new mapping type 'dvstring'.
'dvstring' behaves like a 'string' with an extra analyzer 'index_docvalues_analyzer' that first token is stored as a DocValue: overcoming the limitation that "An analyzed string cannot be stored use doc values".

USAGE
=====
```
echo "Create a superhero type in the test index that name property"
echo " is indexed as lowercased and persisted as a docvalue"
curl -XPOST localhost:9200/test -d '{
    "settings" : {
        "number_of_shards" : 1
    },
    "mappings" : {
        "superheros" : {
            "properties" : {
                "name" : { "type" : "dvstring" }
            }
        }
    }
}
```


WARNING
=======
It works on a standard production setup of Elasticsearch.
I have been using this code for 5 months and it does sort everytime correctly (Disclaimer: this code does not come with any guaranty).

However when I run the randomised test it often does not pass.
I have not figured out yet what happens.

Anyways:
```
echo "Expected to pass"
mvn clean test -Dtests.seed=4913758934982201184 -Dtests.class=org.elasticsearch.docvalues.string.DVStringMapperTests -Dtests.prefix=tests -Dfile.encoding=US-ASCII -Duser.timezone=Asia/Singapore -Dtests.method="testIt" -Dtests.processors=8

echo "Expected to fail"
mvn clean test -Dtests.seed=87762705122378A0 -Dtests.class=org.elasticsearch.docvalues.string.DVStringMapperTests -Dtests.prefix=tests -Dfile.encoding=US-ASCII -Duser.timezone=Asia/Singapore -Dtests.method="testIt" -Dtests.processors=8
```


LICENSE and COPYRIGHT
=====================
Most of the DVStringFieldMapper is in fact coming straight from the original StringFieldMapper.
So License and Copyright are kept the same than Elasticsearch

HELP WANTED
===========

* Make the randomised tests  pass everytime.
* Add parameters to support pluging a custom analyzer: at the moment the analyzer is hardcoded to lower-case the input.