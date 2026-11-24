package com.playtomic.tests.common.contracts.models;


public interface ILogDescriptor
        extends IModel {

    Object getObject();

    String getMethodName();

    String getTimestamp();
}
