package com.mypopsy.floatingsearchview.demo.hilt.entrypoint;

import com.mypopsy.floatingsearchview.demo.search.GoogleSearch;

import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;

@EntryPoint
@InstallIn(SingletonComponent.class)

public interface GoogleSearchControllerEntryPoint {

  public GoogleSearch getGoogleSearch();
}
