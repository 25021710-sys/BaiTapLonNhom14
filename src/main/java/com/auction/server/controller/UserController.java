package com.auction.server.controller;

import com.auction.common.request.LoginRequest;
import com.auction.common.request.RegisterRequest;
import com.auction.common.request.UpdateProfileRequest;
import com.auction.common.request.BalanceRequest;

import com.auction.common.response.LoginResponse;
import com.auction.common.response.RegisterResponse;
import com.auction.common.response.UpdateProfileResponse;
import com.auction.common.response.BalanceResponse;

import com.auction.server.service.AuthService;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class UserController {
    private final AuthService authService = new AuthService();

    public void processRequest(String action, ObjectInputStream in, ObjectOutputStream out) throws Exception {
        switch (action) {
            case "USER_LOGIN":
                LoginRequest loginReq = (LoginRequest) in.readObject();
                LoginResponse loginRes = authService.login(loginReq);
                out.writeObject(loginRes);                              // Ném kết quả về Client
                out.flush();
                break;

            case "USER_REGISTER":
                RegisterRequest regReq = (RegisterRequest) in.readObject();
                RegisterResponse regRes = authService.register(regReq);
                out.writeObject(regRes);
                out.flush();
                break;

            case "USER_UPDATE_PROFILE":
                UpdateProfileRequest upReq = (UpdateProfileRequest) in.readObject();
                UpdateProfileResponse upRes = authService.updateProfile(upReq);
                out.writeObject(upRes);
                out.flush();
                break;

            case "USER_BALANCE":
                BalanceRequest balReq = (BalanceRequest) in.readObject();
                BalanceResponse balRes = authService.handleBalance(balReq);
                out.writeObject(balRes);
                out.flush();
                break;
        }
    }
}