package com.dumper.server.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Getter
@RequiredArgsConstructor
public enum Version {

    FIRST_GROUP(Arrays.asList(2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008)),
    SECOND_GROUP(Arrays.asList(2009, 2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020));

    private final List<Integer> versions;
}
