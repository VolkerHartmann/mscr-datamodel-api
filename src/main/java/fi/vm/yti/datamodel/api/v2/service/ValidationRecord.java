package fi.vm.yti.datamodel.api.v2.service;

import java.util.List;

record ValidationRecord(boolean status, List<String> messages) {

}
