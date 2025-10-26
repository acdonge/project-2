/**
Copyright (c) 2024 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import uga.menik.csx370.models.FollowableUser;
import uga.menik.csx370.models.User;
import uga.menik.csx370.services.PeopleService;
import uga.menik.csx370.services.UserService;

/**
 * Handles /people URL and its sub URL paths.
 */
@Controller
@RequestMapping("/people")
public class PeopleController {

    // User and people services are injected so we can access the current user and query followable users.
    private final UserService userService;
    private final PeopleService peopleService;

    @Autowired
    public PeopleController(UserService userService, PeopleService peopleService) {
        this.userService = userService;
        this.peopleService = peopleService;
    }

    /**
     * Serves the /people web page.
     * 
     * Note that this accepts a URL parameter called error.
     * The value to this parameter can be shown to the user as an error message.
     * See notes in HashtagSearchController.java regarding URL parameters.
     */
    @GetMapping
    public ModelAndView webpage(@RequestParam(name = "error", required = false) String error) {
        // See notes on ModelAndView in BookmarksController.java.
        ModelAndView mv = new ModelAndView("people_page");

        List<FollowableUser> followableUsers = List.of();
        String errorMessage = error;

        try {
            User loggedInUser = userService.getLoggedInUser();
            if (loggedInUser == null) {
                return new ModelAndView("redirect:/login");
            }

            followableUsers = peopleService.getFollowableUsers(loggedInUser.getUserId());
        } catch (RuntimeException ex) {
            System.err.println("Failed to load followable users: " + ex.getMessage());
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = "Unable to load the people list at this time.";
            }
        }

        mv.addObject("users", followableUsers);

        mv.addObject("errorMessage", errorMessage);

        // Enable the following line if you want to show no content message.
        // Do that if your content list is empty.
        if (followableUsers.isEmpty()) {
            mv.addObject("isNoContent", true);
        }
        
        return mv;
    }

    /**
     * This function handles user follow and unfollow.
     * Note the URL has parameters defined as variables ie: {userId} and {isFollow}.
     * Follow and unfollow is handled by submitting a get type form to this URL 
     * by specifing the userId and the isFollow variables.
     * Learn more here: https://www.w3schools.com/tags/att_form_method.asp
     * An example URL that is handled by this function looks like below:
     * http://localhost:8081/people/1/follow/false
     * The above URL assigns 1 to userId and false to isFollow.
     */
    @GetMapping("{userId}/follow/{isFollow}")
    public String followUnfollowUser(@PathVariable("userId") String userId,
            @PathVariable("isFollow") Boolean isFollow) {
        User loggedInUser = userService.getLoggedInUser();
        if (loggedInUser == null) {
            return "redirect:/login";
        }

        try {
            if (Boolean.TRUE.equals(isFollow)) {
                peopleService.followUser(loggedInUser.getUserId(), userId);
            } else {
                peopleService.unfollowUser(loggedInUser.getUserId(), userId);
            }
            return "redirect:/people";
        } catch (RuntimeException ex) {
            System.err.println("Failed to update follow state: " + ex.getMessage());
            String message = URLEncoder.encode("Failed to (un)follow the user. Please try again.",
                    StandardCharsets.UTF_8);
            return "redirect:/people?error=" + message;
        }
    }

}
