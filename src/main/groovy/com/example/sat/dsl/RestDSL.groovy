package com.example.sat.dsl

import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@GroovyASTTransformationClass("com.example.sat.dsl.RestDSLTransformation")
@interface RestDSL {}