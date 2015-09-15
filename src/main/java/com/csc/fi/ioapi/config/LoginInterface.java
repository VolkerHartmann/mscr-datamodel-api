/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.csc.fi.ioapi.config;

import javax.servlet.http.HttpSession;

/**
 *
 * @author malonen
 */
public interface LoginInterface {
    
    public boolean isLoggedIn();
    
    public boolean isInGroup(String group);
   
    public String getDisplayName();
    
    public String getEmail();
    
    public String[] getGroupUris();
    
}