#!/bin/bash

java -jar ../libs/dko.jar generate-dkos \
    --schemas ../schemas.json \
    --package org.asmic.mta.dko \
    --java-output-dir ../gen
