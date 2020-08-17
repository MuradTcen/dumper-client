package com.dumper.server.entity;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

@RequiredArgsConstructor
@Entity
@Table(name = "backupset")
@Data
public class Backupset implements Serializable {

    @Id
    @Column
    private int backupSetId;

}
