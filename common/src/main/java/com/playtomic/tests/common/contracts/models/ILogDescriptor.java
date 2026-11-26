package com.playtomic.tests.common.contracts.models;


/** Identifies the object, method and timestamp associated with a log entry. */
public interface ILogDescriptor
        extends IModel {

    /** Returns the object identified by the log descriptor. */
    Object getObject();

    /** Returns the method identified by the log descriptor. */
    String getMethodName();

    /** Returns the timestamp associated with this record. */
    String getTimestamp();
}
