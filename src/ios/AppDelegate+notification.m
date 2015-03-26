//
//  AppDelegate+notification.m
//  pushtest
//
//  Created by Robert Easterday on 10/26/12.
//
//

#import "AppDelegate+notification.h"
#import "PushPlugin.h"
#import <objc/runtime.h>

typedef void(^BackgroundCompletionHandler)(UIBackgroundFetchResult);

static char launchNotificationKey;
BackgroundCompletionHandler _backgroundCompletionHandler;
NSURLSessionConfiguration *backgroundConfiguration;
NSURLSession *session;

@implementation AppDelegate (notification)

- (id) getCommandInstance:(NSString*)className
{
    return [self.viewController getCommandInstance:className];
}

// its dangerous to override a method from within a category.
// Instead we will use method swizzling. we set this up in the load call.
+ (void)load
{
    Method original, swizzled;

    original = class_getInstanceMethod(self, @selector(init));
    swizzled = class_getInstanceMethod(self, @selector(swizzled_init));
    method_exchangeImplementations(original, swizzled);
}

- (AppDelegate *)swizzled_init
{
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(createNotificationChecker:)
               name:@"UIApplicationDidFinishLaunchingNotification" object:nil];

    // This actually calls the original init method over in AppDelegate. Equivilent to calling super
    // on an overrided method, this is not recursive, although it appears that way. neat huh?
    return [self swizzled_init];
}

// This code will be called immediately after application:didFinishLaunchingWithOptions:. We need
// to process notifications in cold-start situations
- (void)createNotificationChecker:(NSNotification *)notification
{
    if (notification)
    {
        NSDictionary *launchOptions = [notification userInfo];
        if (launchOptions)
            self.launchNotification = [launchOptions objectForKey: @"UIApplicationLaunchOptionsRemoteNotificationKey"];
    }
}

- (void)application:(UIApplication *)application didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken {
    PushPlugin *pushHandler = [self getCommandInstance:@"PushPlugin"];
    [pushHandler didRegisterForRemoteNotificationsWithDeviceToken:deviceToken];
}

- (void)application:(UIApplication *)application didFailToRegisterForRemoteNotificationsWithError:(NSError *)error {
    PushPlugin *pushHandler = [self getCommandInstance:@"PushPlugin"];
    [pushHandler didFailToRegisterForRemoteNotificationsWithError:error];
}

- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo {
    NSLog(@"didReceiveNotification normal");

    // Get application state for iOS4.x+ devices, otherwise assume active
    UIApplicationState appState = UIApplicationStateActive;
    if ([application respondsToSelector:@selector(applicationState)]) {
        appState = application.applicationState;
    }

    if (appState == UIApplicationStateActive) {
        PushPlugin *pushHandler = [self getCommandInstance:@"PushPlugin"];
        pushHandler.notificationMessage = userInfo;
        pushHandler.isInline = YES;
        [pushHandler notificationReceived];
    } else {
        //save it for later
        self.launchNotification = userInfo;
    }
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error {
   NSLog(@"didcompletewith error %@",[error localizedDescription]);
    _backgroundCompletionHandler(UIBackgroundFetchResultFailed);
}

-(void)URLSessionDidFinishEventsForBackgroundURLSession:(NSURLSession *)session {
    NSLog(@"URLSessionDidFinishEventsForBackgroundURLSession");
    _backgroundCompletionHandler(UIBackgroundFetchResultNewData);
}



- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo fetchCompletionHandler:(void (^)(UIBackgroundFetchResult result))handler
{
    // store the completition handler to be called from the delegate
    _backgroundCompletionHandler = handler;

    NSLog(@"didReceiveNotification");
    for(NSString *key in [userInfo allKeys]) {
        NSLog(@"%@",[userInfo objectForKey:key]);
    }
    // server address, needs to be stored by wizard in main app
    //NSString *address = @"http://dovetest.oxoe.int/appsuite/api/login/httpAuth";
    // create valid url and POST request and request body
    //NSURL *url = [NSURL URLWithString:address];

    //NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    //[request setHTTPMethod:@"POST"];

    // create basic auth params
    //NSString *authStr = @"user2@dovetest.oxoe.int:secret";
    //NSData *authData = [authStr dataUsingEncoding:NSUTF8StringEncoding];
    //NSString *authValue = [NSString stringWithFormat: @"Basic %@",[authData base64EncodedStringWithOptions:0]];
    //[request setValue:authValue forHTTPHeaderField:@"Authorization"];

    // Get application state for iOS4.x+ devices, otherwise assume active

    UIApplicationState appState = UIApplicationStateActive;

    if ([application respondsToSelector:@selector(applicationState)]) {
        appState = application.applicationState;
    }
    // app is active/forground, pass push to running app
    if (appState == UIApplicationStateActive) {
        PushPlugin *pushHandler = [self getCommandInstance:@"PushPlugin"];
        pushHandler.notificationMessage = userInfo;
        pushHandler.isInline = YES;
        [pushHandler notificationReceived];

        // call when work is done
        handler(UIBackgroundFetchResultNewData);

    } else if(false && appState == UIApplicationStateBackground) {
        if ([userInfo valueForKeyPath:@"aps.content-available"]) {

            NSLog(@"Background relogin needed");

          /*  NSURLSessionDownloadTask *downloadTask = [session downloadTaskWithRequest:request];
            //NSURLSessionUploadTask *uTask = [session uploadTaskWithRequest:request fromFile:fileURL];

            NSLog(@"Starting relogin via basic auth");

            [downloadTask resume];*/
        }
    } else {
        NSLog(@"Background but not relogin");
        //save it for later
        self.launchNotification = userInfo;
        // call when work is done
        handler(UIBackgroundFetchResultNewData);

    }

}
/*
- (void)application:(UIApplication *)application didReceiveRemoteNotification:(NSDictionary *)userInfo {
    NSLog(@"didReceiveNotification");

    // Get application state for iOS4.x+ devices, otherwise assume active
    UIApplicationState appState = UIApplicationStateActive;
    if ([application respondsToSelector:@selector(applicationState)]) {
        appState = application.applicationState;
    }

    if (appState == UIApplicationStateActive) {
        PushPlugin *pushHandler = [self getCommandInstance:@"PushPlugin"];
        pushHandler.notificationMessage = userInfo;
        pushHandler.isInline = YES;
        [pushHandler notificationReceived];
    } else {
        //save it for later
        self.launchNotification = userInfo;
    }
}
*/

- (void)applicationDidBecomeActive:(UIApplication *)application {

    NSLog(@"active");

    backgroundConfiguration = [NSURLSessionConfiguration backgroundSessionConfigurationWithIdentifier:@"com.open-xchange.mailapp"];
    session = [NSURLSession sessionWithConfiguration:backgroundConfiguration delegate:self delegateQueue:[NSOperationQueue mainQueue]];


    //zero badge
    application.applicationIconBadgeNumber = 0;

    if (self.launchNotification) {
        PushPlugin *pushHandler = [self getCommandInstance:@"PushPlugin"];

        pushHandler.notificationMessage = self.launchNotification;
        self.launchNotification = nil;
        [pushHandler performSelectorOnMainThread:@selector(notificationReceived) withObject:pushHandler waitUntilDone:NO];
    }
}

// The accessors use an Associative Reference since you can't define a iVar in a category
// http://developer.apple.com/library/ios/#documentation/cocoa/conceptual/objectivec/Chapters/ocAssociativeReferences.html
- (NSMutableArray *)launchNotification
{
   return objc_getAssociatedObject(self, &launchNotificationKey);
}

- (void)setLaunchNotification:(NSDictionary *)aDictionary
{
    objc_setAssociatedObject(self, &launchNotificationKey, aDictionary, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

- (void)dealloc
{
    self.launchNotification = nil; // clear the association and release the object
}

@end
