package com.cloudbees.eclipse.dtp;


public interface Images {

  String ICONS = "icons";
  String ICONS16 = ICONS + "/16x16";
  
  String JDBC_16_PATH = ICONS + "/jdbc_16.gif";
  String JDBC_16_ICON = Images.class.getSimpleName() + ".jdbc_16.gif";

  String CLOUDBEES_ICON_16x16_PATH = ICONS16 + "/cb_plain.png";
  String CLOUDBEES_ICON_16x16 = Images.class.getSimpleName() + ".cb_plain.png";

  String CLOUDBEES_FOLDER = Images.class.getSimpleName() + ".cb_folder_cb.png";
  String CLOUDBEES_FOLDER_PATH = ICONS16 + "/cb_folder_cb.png";

  String CLOUDBEES_REFRESH = Images.class.getSimpleName() + ".refresh.png";
  String CLOUDBEES_REFRESH_PATH = ICONS16 + "/refresh.png";
}
